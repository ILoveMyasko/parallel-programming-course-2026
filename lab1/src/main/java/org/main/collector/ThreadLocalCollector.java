package org.main.collector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalCollector implements MetricsCollector {
    private final int BUCKET_COUNT = 256;
    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int b = (int) Math.min(value / 4, 255);

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        // min и max обновляются обычным сравнением без CAS:
        if (value < s.min.getPlain()) s.min.setRelease(value);
        if (value > s.max.getPlain()) s.max.setRelease(value);
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> copyOfStates;
        synchronized (listLock) {
            copyOfStates = new ArrayList<>(allStates);
        }
        long[] out = new long[256];
        long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

        for (ThreadState s : copyOfStates) {
            for (int i = 0; i < 256; i++) out[i] += s.buckets.get(i); // C++: .load(relaxed)
            count += s.count.get();

            sum += s.sum.get();
            min = Math.min(min, s.min.get());
            max = Math.max(max, s.max.get());
        }
        // считаем p50 и p99 кумулятивно по массиву out (см. Этап 0)
        long p50 = percentile(out, count, 50);
        long p99 = percentile(out, count, 99);
        return new Snapshot(out, count, sum, min, max, p50, p99);
    }

     public long percentile(long[] buckets, long count,  int percentile){
        if (percentile > 100) {
            throw new IllegalArgumentException("percentile <= 100");
        }
        long threshold = (long) Math.ceil(count * (percentile / 100.0));;
        long accumulated = 0;
        for (int i = 0; i < BUCKET_COUNT; i++){
            accumulated += buckets[i];
            if (accumulated >= threshold) return i * 4;
        }
        return 0;
    }


    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

}
