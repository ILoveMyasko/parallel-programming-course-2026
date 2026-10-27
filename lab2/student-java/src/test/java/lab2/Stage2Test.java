package test.java.lab2;

import lab2.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Этап 2: Тесты SchedLock, обнаружения Deadlock и стратегии Replay")
public class Stage2Test {

    @Test
    @DisplayName("SchedLock: последовательный захват и освобождение замка")
    void testSchedLockSequential() {
        Scheduler s = new Scheduler();
        s.logging = true;
        SchedLock lock = new SchedLock(s, "testLock");

        s.spawn(() -> {
            lock.lock();
            lock.unlock();
        });

        s.run(new Strategies.FirstAlive());

        assertEquals("AA", s.traceString());
        assertEquals(List.of("A  lock testLock", "A  unlock testLock"), s.log);
    }

    @Test
    @DisplayName("withdraw-v1 с FirstAlive: выдаёт эталонную трассу AAAABB")
    void testWithdrawV1FirstAlive() {
        Targets.Target target = Targets.byName("withdraw-v1").get();
        Scheduler s = new Scheduler();
        s.logging = true;
        target.setup(s);

        s.run(new Strategies.FirstAlive());

        assertEquals("AAAABB", s.traceString());
        assertNull(target.check());

        List<String> expectedLog = List.of(
                "A  lock mu",
                "A  unlock mu",
                "A  window",
                "A  lock mu",
                "A  unlock mu",
                "B  lock mu",
                "B  unlock mu",
                "B  window"
        );
        assertEquals(expectedLog, s.log);
    }

    @Test
    @DisplayName("transfer с FirstAlive: выдаёт эталонную трассу AAAABBBB -> ok")
    void testTransferFirstAlive() {
        Targets.Target target = Targets.byName("transfer").get();
        Scheduler s = new Scheduler();
        target.setup(s);

        s.run(new Strategies.FirstAlive());

        assertEquals("AAAABBBB", s.traceString());
        assertNull(target.check());
    }

    @Test
    @DisplayName("transfer со стратегией Replay(AB): фиксирует DEADLOCK held={a:A, b:B}")
    void testTransferDeadlockReplay() {
        Targets.Target target = Targets.byName("transfer").get();
        Scheduler s = new Scheduler();
        s.logging = true;
        target.setup(s);

        s.run(new Strategies.Replay("AB"));

        assertEquals("AB", s.traceString());
        assertEquals("held={a:A, b:B}", s.deadlock);

        List<String> expectedLog = List.of(
                "A  lock a",
                "A  window",
                "B  lock b",
                "B  window"
        );
        assertEquals(expectedLog, s.log);
    }

    @Test
    @DisplayName("Replay: ошибка при невалидном потоке или окончании трассы")
    void testReplayErrors() {
        // Поток не в enabled
        Strategies.Replay replay1 = new Strategies.Replay("C");
        assertThrows(IllegalStateException.class, () -> replay1.pick(List.of(0, 1), -1));

        // Трасса закончилась раньше времени
        Strategies.Replay replay2 = new Strategies.Replay("A");
        replay2.pick(List.of(0, 1), -1);
        assertThrows(IllegalStateException.class, () -> replay2.pick(List.of(0, 1), 0));
    }

    @Test
    @DisplayName("Deadlock: корректная раскрутка и завершение без зависания")
    void testDeadlockCleanTermination() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            Targets.Target target = Targets.byName("transfer").get();
            Scheduler s = new Scheduler();
            target.setup(s);
            s.run(new Strategies.Replay("AB"));
            assertNotNull(s.deadlock);
        });
    }

    @Test
    @DisplayName("SchedLock: взаимное исключение между двумя потоками (поток B ждёт пока A держит лок)")
    void testSchedLockMutualExclusion() {
        Scheduler s = new Scheduler();
        s.logging = true;
        SchedLock lock = new SchedLock(s, "mu");
        Shared<Integer> shared = new Shared<>("val", 0);

        s.spawn(() -> {
            lock.lock();
            shared.set(1);
            lock.unlock();
        });

        s.spawn(() -> {
            lock.lock();
            shared.set(2);
            lock.unlock();
        });

        s.run(new Strategies.FirstAlive());

        // Поток A: lock mu (1), set 1 (2), unlock mu (3) -> AAA
        // Поток B: lock mu (4), set 2 (5), unlock mu (6) -> BBB
        assertEquals("AAABBB", s.traceString());
        assertEquals(2, shared.peek());
        assertEquals(List.of(
                "A  lock mu",
                "A  val = 1",
                "A  unlock mu",
                "B  lock mu",
                "B  val = 2",
                "B  unlock mu"
        ), s.log);
    }

    @Test
    @DisplayName("Replay: лишние символы в конце трассы игнорируются")
    void testReplayWithSuperfluousSuffix() {
        Targets.Target target = Targets.byName("transfer").get();
        Scheduler s = new Scheduler();
        target.setup(s);

        // Трасса дедлока ровно AB, но мы передаем суффикс CDEF
        s.run(new Strategies.Replay("ABCDEF"));

        assertEquals("AB", s.traceString());
        assertEquals("held={a:A, b:B}", s.deadlock);
    }

    @Test
    @DisplayName("Replay.beginRun() сбрасывает индекс для повторного прогона")
    void testReplayBeginRunResetsIndex() {
        Strategies.Replay replay = new Strategies.Replay("AB");
        assertEquals(0, replay.pick(List.of(0, 1), -1));
        assertEquals(1, replay.pick(List.of(0, 1), 0));

        // После beginRun() снова начинает с первого символа
        replay.beginRun();
        assertEquals(0, replay.pick(List.of(0, 1), -1));
        assertEquals(1, replay.pick(List.of(0, 1), 0));
    }

    @Test
    @DisplayName("SchedLock: вызовы lock() и unlock() вне планировщика безопасны")
    void testSchedLockOutsideScheduler() {
        Scheduler s = new Scheduler();
        SchedLock lock = new SchedLock(s, "directLock");

        assertDoesNotThrow(lock::lock);
        assertDoesNotThrow(lock::unlock);
    }

    @Test
    @DisplayName("Deadlock для 3 потоков: корректный формат held замков в порядке создания")
    void testThreeThreadDeadlock() {
        Scheduler s = new Scheduler();
        SchedLock l1 = new SchedLock(s, "l1");
        SchedLock l2 = new SchedLock(s, "l2");
        SchedLock l3 = new SchedLock(s, "l3");

        // Поток A (0): берет l1, затем l2
        s.spawn(() -> {
            l1.lock();
            Scheduler.point();
            l2.lock();
            l2.unlock();
            l1.unlock();
        });

        // Поток B (1): берет l2, затем l3
        s.spawn(() -> {
            l2.lock();
            Scheduler.point();
            l3.lock();
            l3.unlock();
            l2.unlock();
        });

        // Поток C (2): берет l3, затем l1
        s.spawn(() -> {
            l3.lock();
            Scheduler.point();
            l1.lock();
            l1.unlock();
            l3.unlock();
        });

        // Трасса ABC приводит к дедлоку по кругу: A держит l1, B держит l2, C держит l3
        s.run(new Strategies.Replay("ABC"));

        assertEquals("ABC", s.traceString());
        assertEquals("held={l1:A, l2:B, l3:C}", s.deadlock);
    }
}
