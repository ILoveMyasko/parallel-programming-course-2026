package test.java.lab2;

import lab2.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Этап 6: Тесты коллектора метрик и семантики await")
public class Stage6Test {

    @Test
    @DisplayName("6а: collector-nostep3-1x1 находит потерю записи на трассе ABBBAABAAB")
    void testCollectorNoStep3Bug() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int explored = 0;
        int fail = 0;
        String firstFailing = null;

        do {
            Main.Outcome o = Main.runOnce(Targets.byName("collector-nostep3-1x1"), dfs, false);
            explored++;
            if (o.failure() != null) {
                fail++;
                if (firstFailing == null) firstFailing = o.trace();
            }
        } while (dfs.endRun());

        assertEquals(106, explored);
        assertEquals(12, fail);
        assertEquals("ABBBAABAAB", firstFailing);
    }

    @Test
    @DisplayName("6б: collector-nostep3-2x1 рост пространств при bounded 0, 1, 2")
    void testCollectorNoStep3BoundedGrowth() {
        // bounded 0: 6 расписаний, 0 ошибок
        assertEquals(6, countExplored(Targets.byName("collector-nostep3-2x1"), 0));
        assertEquals(0, countFails(Targets.byName("collector-nostep3-2x1"), 0));

        // bounded 1: 102 расписания, 0 ошибок
        assertEquals(102, countExplored(Targets.byName("collector-nostep3-2x1"), 1));
        assertEquals(0, countFails(Targets.byName("collector-nostep3-2x1"), 1));

        // bounded 2: 945 расписаний, 10 ошибок
        assertEquals(945, countExplored(Targets.byName("collector-nostep3-2x1"), 2));
        assertEquals(10, countFails(Targets.byName("collector-nostep3-2x1"), 2));
    }

    @Test
    @DisplayName("6в: collector-plain-1x1 даёт 0 ошибок (29 расписаний, планировщик слеп к plain int)")
    void testCollectorPlainBlind() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int explored = 0;
        int fail = 0;

        do {
            Main.Outcome o = Main.runOnce(Targets.byName("collector-plain-1x1"), dfs, false);
            explored++;
            if (o.failure() != null) fail++;
        } while (dfs.endRun());

        assertEquals(29, explored);
        assertEquals(0, fail);
    }

    @Test
    @DisplayName("6в: collector-acqrel-1x1 даёт 0 ошибок (636 расписаний, DFS не видит Store Buffering)")
    void testCollectorAcqRelBlind() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int explored = 0;
        int fail = 0;

        do {
            Main.Outcome o = Main.runOnce(Targets.byName("collector-acqrel-1x1"), dfs, false);
            explored++;
            if (o.failure() != null) fail++;
        } while (dfs.endRun());

        assertEquals(636, explored);
        assertEquals(0, fail);
    }

    @Test
    @DisplayName("6в: collector-ok-1x1 даёт 0 ошибок (636 расписаний)")
    void testCollectorOk() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int explored = 0;
        int fail = 0;

        do {
            Main.Outcome o = Main.runOnce(Targets.byName("collector-ok-1x1"), dfs, false);
            explored++;
            if (o.failure() != null) fail++;
        } while (dfs.endRun());

        assertEquals(636, explored);
        assertEquals(0, fail);
    }

    @Test
    @DisplayName("Механика await: поток не блокируется, если условие сразу истинно и состояние не трогалось")
    void testAwaitMechanicsNonBlockingWhenTrue() {
        Scheduler s = new Scheduler();
        s.logging = true;
        s.spawn(() -> {
            Scheduler.await(() -> true);
        });

        s.run(new Strategies.FirstAlive());
        assertEquals("A", s.traceString());
        assertTrue(s.log.contains("A  await done"));
    }

    @Test
    @DisplayName("Механика await: поток B не входит в enabled до тех пор, пока поток A не выполнит условие")
    void testAwaitBlocksUntilConditionMet() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<Integer> flag = new Shared<>("flag", 0);

        s.spawn(() -> {
            // Поток A ждет 1 квант, затем выставляет flag = 1
            flag.set(1);
        });

        s.spawn(() -> {
            // Поток B сразу вызывает await, ждет flag == 1
            Scheduler.await(() -> flag.peek() == 1);
        });

        // Поток A должен сделать первый шаг, только после этого поток B может проснуться
        s.run(new Strategies.FirstAlive());
        assertEquals("AB", s.traceString());
        assertTrue(s.log.contains("B  await done"));
    }

    private int countExplored(Supplier<Targets.Target> f, int bound) {
        Strategies.Dfs dfs = new Strategies.Dfs(bound);
        int count = 0;
        do {
            Main.runOnce(f, dfs, false);
            count++;
        } while (dfs.endRun());
        return count;
    }

    private int countFails(Supplier<Targets.Target> f, int bound) {
        Strategies.Dfs dfs = new Strategies.Dfs(bound);
        int fails = 0;
        do {
            Main.Outcome o = Main.runOnce(f, dfs, false);
            if (o.failure() != null) fails++;
        } while (dfs.endRun());
        return fails;
    }
}
