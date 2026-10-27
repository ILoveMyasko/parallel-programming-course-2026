package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    Scheduler.ThreadContext owner;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        s.registerLock(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.ThreadContext ctx = Scheduler.getCurrentContext();
        if (ctx == null) {
            return;
        }
        ctx.waitingOnLock = this;

        if (ctx.touchedShared || this.owner != null) {
            ctx.pause(null);
        }

        if (ctx.terminated || ctx.scheduler.deadlock != null) {
            this.owner = null;
            ctx.waitingOnLock = null;
            throw new Scheduler.DeadlockUnwindException();
        }
        this.owner = ctx;
        ctx.waitingOnLock = null;
        Scheduler.logOperation("lock " + name);
        Scheduler.markSharedTouched();
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        Scheduler.ThreadContext ctx = Scheduler.getCurrentContext();
        if (ctx == null) {
            return;
        }
        if (ctx.terminated || ctx.scheduler.deadlock != null) {
            this.owner = null;
            return;
        }
        Scheduler.pauseIfNeeded();
        if (ctx.terminated || ctx.scheduler.deadlock != null) {
            this.owner = null;
            return;
        }
        this.owner = null;
        Scheduler.logOperation("unlock " + name);
        Scheduler.markSharedTouched();
    }
}
