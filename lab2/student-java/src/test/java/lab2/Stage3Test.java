package test.java.lab2;

import lab2.Main;
import lab2.Strategies;
import lab2.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Этап 3: Тесты стратегии RandomPick(seed)")
public class Stage3Test {

    @Test
    @DisplayName("RandomPick: воспроизводимость выбора потока с одинаковым seed")
    void testRandomPickReproducibility() {
        Strategies.RandomPick r1 = new Strategies.RandomPick(42L);
        Strategies.RandomPick r2 = new Strategies.RandomPick(42L);

        List<Integer> enabled = List.of(0, 1, 2);
        for (int i = 0; i < 20; i++) {
            assertEquals(r1.pick(enabled, -1), r2.pick(enabled, -1));
        }
    }

    @Test
    @DisplayName("RandomPick.beginRun(): сброс генератора для повторного воспроизведения")
    void testRandomPickBeginRun() {
        Strategies.RandomPick r = new Strategies.RandomPick(12345L);
        List<Integer> enabled = List.of(0, 1);

        int firstRun1 = r.pick(enabled, -1);
        int firstRun2 = r.pick(enabled, firstRun1);

        r.beginRun();

        int secondRun1 = r.pick(enabled, -1);
        int secondRun2 = r.pick(enabled, secondRun1);

        assertEquals(firstRun1, secondRun1);
        assertEquals(firstRun2, secondRun2);
    }

    @Test
    @DisplayName("withdraw-v1 с randoms 1000: воспроизводимый поиск ошибок и 10/10 повтор")
    void testWithdrawV1Randoms() {
        int count = 1000;
        int fail = 0;
        long firstFailingSeed = -1;
        String failingTrace = null;

        for (long seed = 0; seed < count; seed++) {
            Main.Outcome o = Main.runOnce(Targets.byName("withdraw-v1"), new Strategies.RandomPick(seed), false);
            if (o.failure() != null) {
                fail++;
                if (firstFailingSeed < 0) {
                    firstFailingSeed = seed;
                    failingTrace = o.trace();
                }
            }
        }

        assertTrue(fail > 0, "Должны быть обнаружены сбои (около 50%)");
        assertEquals(0L, firstFailingSeed, "Первый ошибочный сид должен быть 0");
        assertNotNull(failingTrace);

        // Проверка повторения 10/10
        int sameFailures = 0;
        for (int i = 0; i < 10; i++) {
            Main.Outcome r = Main.runOnce(Targets.byName("withdraw-v1"), new Strategies.Replay(failingTrace), false);
            if (failingTrace.equals(r.trace()) && r.failure() != null && r.failure().contains("OVERDRAFT")) {
                sameFailures++;
            }
        }
        assertEquals(10, sameFailures, "Трасса ошибки должна воспроизводиться 10 из 10 раз");
    }

    @Test
    @DisplayName("RandomPick: выбор строго из элементов enabled (даже для нетривиальных подмножеств)")
    void testRandomPickOnlyReturnsEnabled() {
        Strategies.RandomPick r = new Strategies.RandomPick(999L);
        List<Integer> enabledSubset = List.of(1, 3, 7);

        for (int i = 0; i < 50; i++) {
            int picked = r.pick(enabledSubset, -1);
            assertTrue(enabledSubset.contains(picked), "Выбранный поток обязан присутствовать в enabled");
        }
    }

    @Test
    @DisplayName("RandomPick: повторный запуск прогона с тем же сидом даёт 100% идентичную трассу")
    void testRandomRunDeterministicOutcome() {
        long seed = 42L;
        Main.Outcome first = Main.runOnce(Targets.byName("withdraw-v1"), new Strategies.RandomPick(seed), false);

        for (int i = 0; i < 5; i++) {
            Main.Outcome repeated = Main.runOnce(Targets.byName("withdraw-v1"), new Strategies.RandomPick(seed), false);
            assertEquals(first.trace(), repeated.trace(), "Трасса прогона с одинаковым seed должна полностью совпадать");
            assertEquals(first.failure(), repeated.failure(), "Исход прогона с одинаковым seed должен полностью совпадать");
        }
    }

    @Test
    @DisplayName("transfer с RandomPick: обнаружение Deadlock и верификация повтором 10/10")
    void testTransferRandomDeadlockDetection() {
        int count = 100;
        int deadlockCount = 0;
        String deadlockTrace = null;

        for (long seed = 0; seed < count; seed++) {
            Main.Outcome o = Main.runOnce(Targets.byName("transfer"), new Strategies.RandomPick(seed), false);
            if (o.failure() != null && o.failure().contains("DEADLOCK")) {
                deadlockCount++;
                if (deadlockTrace == null) {
                    deadlockTrace = o.trace();
                }
            }
        }

        assertTrue(deadlockCount > 0, "Случайный поиск должен обнаруживать Deadlock в transfer");
        assertNotNull(deadlockTrace);

        // Верификация воспроизводимости через Replay
        int replaySuccess = 0;
        for (int i = 0; i < 10; i++) {
            Main.Outcome r = Main.runOnce(Targets.byName("transfer"), new Strategies.Replay(deadlockTrace), false);
            if (deadlockTrace.equals(r.trace()) && r.failure() != null && r.failure().contains("DEADLOCK")) {
                replaySuccess++;
            }
        }
        assertEquals(10, replaySuccess, "Дедлок должен стабильно воспроизводиться 10 из 10 раз");
    }

    @Test
    @DisplayName("withdraw-v2 с RandomPick: 0 ошибок на 100 случайных сидах")
    void testWithdrawV2RandomNoFailures() {
        for (long seed = 0; seed < 100; seed++) {
            Main.Outcome o = Main.runOnce(Targets.byName("withdraw-v2"), new Strategies.RandomPick(seed), false);
            assertNull(o.failure(), "В корректной версии withdraw-v2 не должно быть ошибок ни при каком сиде");
        }
    }
}
