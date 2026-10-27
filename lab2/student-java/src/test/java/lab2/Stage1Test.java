package test.java.lab2;

import lab2.Scheduler;
import lab2.Shared;
import lab2.Strategies;
import lab2.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Этап 1: Тесты базового планировщика, Shared<T> и FirstAlive")
public class Stage1Test {

    @Test
    @DisplayName("Shared: peek() возвращает значение без побочных эффектов")
    void testSharedPeek() {
        Shared<Integer> shared = new Shared<>("testVar", 42);
        assertEquals(42, shared.peek());
        shared.set(100);
        assertEquals(100, shared.peek());
    }

    @Test
    @DisplayName("Shared: getAcquire() и setRelease() эквивалентны get() и set()")
    void testSharedAcquireRelease() {
        Shared<String> shared = new Shared<>("strVar", "initial");
        assertEquals("initial", shared.getAcquire());
        shared.setRelease("updated");
        assertEquals("updated", shared.get());
    }

    @Test
    @DisplayName("Стратегия FirstAlive: всегда выбирает первый доступный поток")
    void testFirstAliveStrategy() {
        Strategies.FirstAlive strategy = new Strategies.FirstAlive();
        assertEquals(0, strategy.pick(List.of(0, 1, 2), -1));
        assertEquals(1, strategy.pick(List.of(1, 2), 0));
        assertEquals(2, strategy.pick(List.of(2), 1));
    }

    @Test
    @DisplayName("Scheduler: регистрация потоков и присвоение букв A, B, C...")
    void testThreadLetterAssignment() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<Integer> x = new Shared<>("x", 0);

        s.spawn(() -> x.set(1));
        s.spawn(() -> x.set(2));
        s.spawn(() -> x.set(3));

        s.run(new Strategies.FirstAlive());

        assertEquals("ABC", s.traceString());
        assertEquals(3, s.log.size());
        assertTrue(s.log.get(0).startsWith("A  x = 1"));
        assertTrue(s.log.get(1).startsWith("B  x = 2"));
        assertTrue(s.log.get(2).startsWith("C  x = 3"));
    }

    @Test
    @DisplayName("Правило «Без пауз подряд»: отсутствие лишних вытеснений")
    void testNoConsecutivePausesWithoutSharedAccess() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<Integer> shared = new Shared<>("val", 0);

        s.spawn(() -> {
            // Первая операция Shared -> пауза перед ней
            shared.set(10);
            // Локальные операции не трогают Shared -> паузы быть не должно
            int a = 1 + 1;
            int b = a * 2;
            assertEquals(4, b);
            // Следующее обращение -> пауза, так как предыдущее было shared.set
            shared.set(20);
        });

        s.run(new Strategies.FirstAlive());
        // Должно быть ровно 2 шага у потока A
        assertEquals("AA", s.traceString());
        assertEquals(2, s.log.size());
        assertEquals("A  val = 10", s.log.get(0));
        assertEquals("A  val = 20", s.log.get(1));
    }

    @Test
    @DisplayName("Scheduler.point(): не делает паузу перед собой, но требует паузу перед следующей операцией")
    void testSchedulerPoint() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<Integer> shared = new Shared<>("x", 0);

        s.spawn(() -> {
            shared.set(1);        // Пауза 1
            Scheduler.point();     // window: НЕ делает паузу, но помечает общее состояние
            shared.set(2);        // Пауза 2 (из-за point)
        });

        s.run(new Strategies.FirstAlive());
        assertEquals("AA", s.traceString());
        assertEquals(3, s.log.size());
        assertEquals("A  x = 1", s.log.get(0));
        assertEquals("A  window", s.log.get(1));
        assertEquals("A  x = 2", s.log.get(2));
    }

    @Test
    @DisplayName("Scheduler.await(): ожидание выполнения условия другим потоком")
    void testSchedulerAwait() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<Integer> ready = new Shared<>("ready", 0);

        // Поток A: ждёт, пока ready не станет равен 1, затем пишет 2
        s.spawn(() -> {
            Scheduler.await(() -> ready.peek() == 1);
            ready.set(2);
        });

        // Поток B: пишет ready = 1
        s.spawn(() -> {
            ready.set(1);
        });

        s.run(new Strategies.FirstAlive());

        // Поток A начинает первым (FirstAlive), но на await блокируется (ready == 0)
        // Затем управление получает поток B, выставляет ready = 1
        // После чего A становится enabled, разблокируется и пишет ready = 2
        assertEquals(2, ready.peek());
        assertTrue(s.log.contains("B  ready = 1"));
        assertTrue(s.log.contains("A  await done"));
        assertTrue(s.log.contains("A  ready = 2"));
    }

    @Test
    @DisplayName("Изоляция потоков: ровно один поток активен во время критических шагов")
    void testStrictSingleThreadExecution() {
        Scheduler s = new Scheduler();
        AtomicInteger activeThreadsCount = new AtomicInteger(0);
        AtomicBoolean invariantViolated = new AtomicBoolean(false);
        Shared<Integer> var = new Shared<>("var", 0);

        Runnable worker = () -> {
            for (int i = 0; i < 10; i++) {
                int val = var.get();
                int active = activeThreadsCount.incrementAndGet();
                if (active > 1) {
                    invariantViolated.set(true);
                }
                // Имитация локальной работы после пробуждения
                Thread.yield();
                activeThreadsCount.decrementAndGet();
                var.set(val + 1);
            }
        };

        s.spawn(worker);
        s.spawn(worker);
        s.spawn(worker);

        s.run(new Strategies.FirstAlive());

        assertFalse(invariantViolated.get(), "Нарушен инвариант: более одного потока бодрствовало одновременно!");
        assertEquals(30, var.peek());
    }

    @Test
    @DisplayName("Интеграционный тест: withdraw-v0 с FirstAlive выдает эталонную трассу AAAB")
    void testWithdrawV0FirstAlive() {
        Targets.Target target = Targets.byName("withdraw-v0").get();
        Scheduler s = new Scheduler();
        s.logging = true;
        target.setup(s);

        s.run(new Strategies.FirstAlive());

        // Проверка эталонной трассы
        assertEquals("AAAB", s.traceString(), "Трасса должна в точности равняться AAAB");

        // Проверка корректности работы программы
        assertNull(target.check(), "Результат check() должен быть null (успех)");

        // Проверка лога шагов
        List<String> expectedLog = List.of(
                "A  balance -> 100",
                "A  window",
                "A  balance -> 100",
                "A  balance = 0",
                "B  balance -> 0",
                "B  window"
        );
        assertEquals(expectedLog, s.log, "Лог операций должен в точности совпадать с эталонным из методички");
    }

    @Test
    @DisplayName("Детерминизм: 50 повторных прогонов withdraw-v0 дают 100% идентичный результат")
    void testWithdrawV0Determinism() {
        for (int i = 0; i < 50; i++) {
            Targets.Target target = Targets.byName("withdraw-v0").get();
            Scheduler s = new Scheduler();
            s.logging = true;
            target.setup(s);
            s.run(new Strategies.FirstAlive());

            assertEquals("AAAB", s.traceString());
            assertNull(target.check());
        }
    }

    @Test
    @DisplayName("Потоки без обращений к общему состоянию завершаются без попадания в trace")
    void testThreadsWithoutSharedOperations() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<Integer> val = new Shared<>("val", 0);

        s.spawn(() -> { /* пустой поток A */ });
        s.spawn(() -> {
            int local = 10 * 20;
            // локальные вычисления без Shared
        });
        s.spawn(() -> val.set(100));

        s.run(new Strategies.FirstAlive());

        assertEquals("C", s.traceString());
        assertEquals(100, val.peek());
        assertEquals(List.of("C  val = 100"), s.log);
    }

    @Test
    @DisplayName("Логирование отключено: при s.logging == false лог остаётся пустым")
    void testLoggingDisabledByDefault() {
        Scheduler s = new Scheduler();
        s.logging = false;
        Shared<Integer> x = new Shared<>("x", 0);

        s.spawn(() -> {
            x.set(1);
            Scheduler.point();
            x.get();
        });

        s.run(new Strategies.FirstAlive());

        assertEquals("AA", s.traceString());
        assertTrue(s.log.isEmpty(), "При отключённом logging лог должен оставаться пустым");
    }

    @Test
    @DisplayName("Shared<T>: поддержка null-значений и строковых типов")
    void testSharedNullHandling() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<String> nullStr = new Shared<>("str", null);

        s.spawn(() -> {
            nullStr.get();
            nullStr.set("hello");
            nullStr.set(null);
        });

        s.run(new Strategies.FirstAlive());

        assertEquals("AAA", s.traceString());
        assertEquals(List.of("A  str -> null", "A  str = hello", "A  str = null"), s.log);
        assertNull(nullStr.peek());
    }

    @Test
    @DisplayName("Вызовы Shared и Scheduler вне контекста потоков планировщика не падают с NPE")
    void testNonSchedulerThreadDirectAccess() {
        Shared<Integer> direct = new Shared<>("direct", 10);
        assertEquals(10, direct.get());
        assertEquals(10, direct.getAcquire());

        direct.set(20);
        assertEquals(20, direct.get());

        direct.setRelease(30);
        assertEquals(30, direct.get());

        assertDoesNotThrow(Scheduler::point);
        assertDoesNotThrow(() -> Scheduler.await(() -> true));
    }

    @Test
    @DisplayName("Несколько вызовов Scheduler.point() подряд не создают лишних пауз")
    void testMultipleConsecutivePoints() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<Integer> x = new Shared<>("x", 0);

        s.spawn(() -> {
            x.set(1);
            Scheduler.point();
            Scheduler.point();
            Scheduler.point();
            x.set(2);
        });

        s.run(new Strategies.FirstAlive());

        assertEquals("AA", s.traceString());
        assertEquals(List.of(
                "A  x = 1",
                "A  window",
                "A  window",
                "A  window",
                "A  x = 2"
        ), s.log);
    }

    @Test
    @DisplayName("Проброс исключений из рабочего потока в вызывающий поток Scheduler.run()")
    void testThreadExceptionPropagation() {
        Scheduler s = new Scheduler();
        s.spawn(() -> {
            throw new IllegalStateException("Test exception from worker thread");
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> s.run(new Strategies.FirstAlive()));
        assertEquals("Test exception from worker thread", ex.getMessage());
    }

    @Test
    @DisplayName("Одиночный поток: корректная последовательность шагов и завершение")
    void testSingleThreadExecution() {
        Scheduler s = new Scheduler();
        s.logging = true;
        Shared<Integer> counter = new Shared<>("counter", 0);

        s.spawn(() -> {
            counter.set(1);
            counter.set(2);
            counter.set(3);
        });

        s.run(new Strategies.FirstAlive());

        assertEquals("AAA", s.traceString());
        assertEquals(3, counter.peek());
        assertEquals(List.of(
                "A  counter = 1",
                "A  counter = 2",
                "A  counter = 3"
        ), s.log);
    }
}
