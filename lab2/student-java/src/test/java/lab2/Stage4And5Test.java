package test.java.lab2;

import lab2.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Этапы 4 и 5: Тесты полного перебора DFS и Bounded DFS")
public class Stage4And5Test {

    @Test
    @DisplayName("Этап 4: Полный перебор DFS для withdraw-v1 находит 6 расписаний и 4 ошибки")
    void testWithdrawV1Dfs() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int explored = 0;
        int fail = 0;
        String firstFailingTrace = null;

        do {
            Main.Outcome o = Main.runOnce(Targets.byName("withdraw-v1"), dfs, false);
            explored++;
            if (o.failure() != null) {
                fail++;
                if (firstFailingTrace == null) {
                    firstFailingTrace = o.trace();
                }
            }
        } while (dfs.endRun());

        assertEquals(6, explored);
        assertEquals(4, fail);
        assertEquals("AABBAABB", firstFailingTrace);
    }

    @Test
    @DisplayName("Этап 4: Полный перебор DFS для withdraw-v2 (2 расписания, 0 ошибок)")
    void testWithdrawV2Dfs() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int explored = 0;
        int fail = 0;

        do {
            Main.Outcome o = Main.runOnce(Targets.byName("withdraw-v2"), dfs, false);
            explored++;
            if (o.failure() != null) fail++;
        } while (dfs.endRun());

        assertEquals(2, explored);
        assertEquals(0, fail);
    }

    @Test
    @DisplayName("Этап 4: Полный перебор DFS для withdraw-v0 (20 расписаний, 18 ошибок, первая AABABB)")
    void testWithdrawV0Dfs() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int explored = 0;
        int fail = 0;
        String firstFailingTrace = null;

        do {
            Main.Outcome o = Main.runOnce(Targets.byName("withdraw-v0"), dfs, false);
            explored++;
            if (o.failure() != null) {
                fail++;
                if (firstFailingTrace == null) {
                    firstFailingTrace = o.trace();
                }
            }
        } while (dfs.endRun());

        assertEquals(20, explored);
        assertEquals(18, fail);
        assertEquals("AABABB", firstFailingTrace);
    }

    @Test
    @DisplayName("Этап 4: Полный перебор DFS для transfer находит дедлок на трассе AB")
    void testTransferDfs() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int explored = 0;
        int fail = 0;
        String firstFailingTrace = null;

        do {
            Main.Outcome o = Main.runOnce(Targets.byName("transfer"), dfs, false);
            explored++;
            if (o.failure() != null) {
                fail++;
                if (firstFailingTrace == null) firstFailingTrace = o.trace();
            }
        } while (dfs.endRun());

        assertEquals(6, explored);
        assertEquals(2, fail);
        assertEquals("AB", firstFailingTrace);
    }

    @Test
    @DisplayName("Этап 4: DFS с одним потоком выполняет ровно 1 расписание")
    void testDfsSingleThreadOnlyOneSchedule() {
        Strategies.Dfs dfs = new Strategies.Dfs(Integer.MAX_VALUE);
        int runs = 0;
        String trace;
        do {
            Scheduler s = new Scheduler();
            Shared<Integer> var = new Shared<>("v", 0);
            s.spawn(() -> {
                var.set(1);
                var.set(2);
            });
            dfs.beginRun();
            s.run(dfs);
            trace = s.traceString();
            runs++;
        } while (dfs.endRun());

        assertEquals(1, runs);
        assertEquals("AA", trace);
    }

    @Test
    @DisplayName("Этап 5: Bounded DFS для withdraw-v1 находит ошибки начиная с K=1")
    void testWithdrawV1Bounded() {
        // K = 0: нет вытеснений -> 2 расписания, 0 ошибок
        assertEquals(0, countFails(Targets.byName("withdraw-v1"), 0));
        assertEquals(2, countExplored(Targets.byName("withdraw-v1"), 0));

        // K = 1: 4 расписания, 2 ошибки
        assertEquals(2, countFails(Targets.byName("withdraw-v1"), 1));
        assertEquals(4, countExplored(Targets.byName("withdraw-v1"), 1));

        // K = 2: 6 расписаний, 4 ошибки
        assertEquals(4, countFails(Targets.byName("withdraw-v1"), 2));
        assertEquals(6, countExplored(Targets.byName("withdraw-v1"), 2));
    }

    @Test
    @DisplayName("Этап 5: Bounded DFS для transfer (K=0: 2 расписания, K=1: 6 расписаний, дедлок на AB)")
    void testTransferBounded() {
        // K = 0: без вытеснений -> 2 расписания, 0 ошибок
        assertEquals(2, countExplored(Targets.byName("transfer"), 0));
        assertEquals(0, countFails(Targets.byName("transfer"), 0));

        // K = 1: 6 расписаний, 2 ошибки
        assertEquals(6, countExplored(Targets.byName("transfer"), 1));
        assertEquals(2, countFails(Targets.byName("transfer"), 1));
    }

    @Test
    @DisplayName("Этап 5: Добровольная передача хода при блокировке или завершении не считается вытеснением")
    void testVoluntaryYieldDoesNotCountAsPreemption() {
        // При K=0 вытеснения запрещены. Но если поток закончил работу,
        // переход хода ко второму потоку ОБЯЗАН произойти (это не вытеснение).
        Strategies.Dfs dfs = new Strategies.Dfs(0);
        int explored = 0;
        do {
            Scheduler s = new Scheduler();
            Shared<Integer> x = new Shared<>("x", 0);
            s.spawn(() -> x.set(1));
            s.spawn(() -> x.set(2));

            dfs.beginRun();
            s.run(dfs);
            explored++;
        } while (dfs.endRun());

        // Два потока: либо сначала A потом B (AB), либо сначала B потом A (BA) -> ровно 2 расписания при K=0!
        assertEquals(2, explored);
    }

    @Test
    @DisplayName("Контрольная точка этапа 5: collector-ok-2x1 bounded 2 даёт ровно 1334 расписания")
    void testCollectorOk2x1Bounded2() {
        int explored = countExplored(Targets.byName("collector-ok-2x1"), 2);
        int fail = countFails(Targets.byName("collector-ok-2x1"), 2);

        assertEquals(1334, explored, "Контрольная точка методички: ровно 1334 расписания");
        assertEquals(0, fail, "В корректном коллекторе не должно быть сбоев");
    }

    private int countExplored(java.util.function.Supplier<Targets.Target> f, int bound) {
        Strategies.Dfs dfs = new Strategies.Dfs(bound);
        int count = 0;
        do {
            Main.runOnce(f, dfs, false);
            count++;
        } while (dfs.endRun());
        return count;
    }

    private int countFails(java.util.function.Supplier<Targets.Target> f, int bound) {
        Strategies.Dfs dfs = new Strategies.Dfs(bound);
        int fails = 0;
        do {
            Main.Outcome o = Main.runOnce(f, dfs, false);
            if (o.failure() != null) fails++;
        } while (dfs.endRun());
        return fails;
    }
}
