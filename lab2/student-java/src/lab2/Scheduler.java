
package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;
    /** Список потоков, которые могут сделать шаг прямо сейчас. */
    final List<Integer> enabled = new ArrayList<>();

    private final List<ThreadContext> threads = new ArrayList<>();
    final Semaphore semSched = new Semaphore(0);
    private final StringBuilder trace = new StringBuilder();
    final List<SchedLock> locks = new ArrayList<>();
    private static final ThreadLocal<ThreadContext> CURRENT = new ThreadLocal<>();

    static final class DeadlockUnwindException extends RuntimeException { }

    static final class ThreadContext {
        final Scheduler scheduler;
        final int id;
        final String letter;
        final Runnable body;
        final Semaphore sem = new Semaphore(0);
        Thread osThread;

        volatile boolean paused = false;
        volatile boolean finished = false;
        volatile boolean touchedShared = true;
        volatile boolean terminated = false;
        volatile BooleanSupplier awaitCond = null;
        volatile SchedLock waitingOnLock = null;
        volatile Throwable uncaughtException = null;

        ThreadContext(Scheduler scheduler, int id, String letter, Runnable body) {
            this.scheduler = scheduler;
            this.id = id;
            this.letter = letter;
            this.body = body;
        }

        void start() {
            osThread = new Thread(() -> {
                CURRENT.set(this);
                try {
                    body.run();
                } catch (DeadlockUnwindException ignored) {
                } catch (Throwable t) {
                    uncaughtException = t;
                } finally {
                    finished = true;
                    paused = false;
                    CURRENT.remove();
                    if (!terminated && scheduler.deadlock == null) {
                        scheduler.semSched.release();
                    }
                }
            }, "SchedThread-" + letter);
            osThread.setDaemon(true);
            osThread.start();
        }

        void pause(BooleanSupplier cond) {
            this.awaitCond = cond;
            this.paused = true;
            //pass turn to scheduler
            scheduler.semSched.release();
            this.sem.acquireUninterruptibly();
            //my turn again
            this.paused = false;
            this.awaitCond = null;
            this.touchedShared = false;
            if (this.terminated || scheduler.deadlock != null) {
                throw new DeadlockUnwindException();
            }
        }
    }



    static ThreadContext getCurrentContext() {
        return CURRENT.get();
    }

    void registerLock(SchedLock lock) {
        locks.add(lock);
    }

    private String formatHeldLocks() {
        List<String> entries = new ArrayList<>();
        for (SchedLock lock : locks) {
            if (lock.owner != null) {
                entries.add(lock.name + ":" + lock.owner.letter);
            }
        }
        return String.join(", ", entries);
    }

    static void pauseIfNeeded() {
        ThreadContext ctx = CURRENT.get();
        if (ctx != null && ctx.touchedShared) {
            ctx.pause(null);
        }
    }

    static void logOperation(String op) {
        ThreadContext ctx = CURRENT.get();
        if (ctx != null && ctx.scheduler.logging && ctx.scheduler.deadlock == null && !ctx.terminated) {
            ctx.scheduler.log.add(ctx.letter + "  " + op);
        }
    }

    static void markSharedTouched() {
        ThreadContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.touchedShared = true;
        }
    }

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        int id = threads.size();
        String letter = String.valueOf((char) ('A' + id));
        threads.add(new ThreadContext(this, id, letter, body));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        trace.setLength(0);
        log.clear();
        deadlock = null;
        enabled.clear();

        for (ThreadContext t : threads) {
            t.start();
            semSched.acquireUninterruptibly();
        }

        int last = -1;
        try {
            while (true) {
                enabled.clear();
                boolean allFinished = true;
                for (ThreadContext t : threads) {
                    if (!t.finished) {
                        allFinished = false;
                        if (t.paused) {
                            if (t.awaitCond != null && !t.awaitCond.getAsBoolean()) {
                                continue;
                            }
                            if (t.waitingOnLock != null && t.waitingOnLock.owner != null) {
                                continue;
                            }
                            enabled.add(t.id);
                        }
                    }
                }

                if (allFinished) {
                    break;
                }

                if (enabled.isEmpty()) {
                    deadlock = "held={" + formatHeldLocks() + "}";
                    for (ThreadContext t : threads) {
                        if (!t.finished) {
                            t.terminated = true;
                            t.sem.release();
                        }
                    }
                    break;
                }

                int pick = strategy.pick(enabled, last);
                last = pick;
                ThreadContext chosen = threads.get(pick);
                trace.append(chosen.letter);

                chosen.sem.release();

                //scheduler turn again
                semSched.acquireUninterruptibly();
            }
        } finally {
            for (ThreadContext t : threads) {
                if (!t.finished) {
                    t.terminated = true;
                    t.sem.release();
                }
            }
        }

        for (ThreadContext t : threads) {
            if (t.osThread != null) {
                try {
                    t.osThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        for (ThreadContext t : threads) {
            if (t.uncaughtException != null && deadlock == null) {
                if (t.uncaughtException instanceof RuntimeException re) throw re;
                if (t.uncaughtException instanceof Error err) throw err;
                throw new RuntimeException(t.uncaughtException);
            }
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        logOperation("window");
        markSharedTouched();
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        ThreadContext ctx = getCurrentContext();
        // if can be deleted
        if (ctx == null) {
            while (!cond.getAsBoolean()) {
                Thread.onSpinWait();
            }
            return;
        }

        if (ctx.touchedShared || !cond.getAsBoolean()) {
            ctx.pause(cond);
        }
        logOperation("await done");
        markSharedTouched();
    }
}
