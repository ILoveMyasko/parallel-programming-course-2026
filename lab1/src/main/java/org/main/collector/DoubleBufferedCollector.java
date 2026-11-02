package org.main.collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferedCollector implements MetricsCollector {
    private final int BUCKET_COUNT = 256;
    private final List<ThreadBuffers> allStates = new ArrayList<>();
    private final Object snapLock = new Object();
    private volatile int active = 0;
    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        ThreadBuffers s = new ThreadBuffers();
        synchronized (snapLock) {
            allStates.add(s);
        }
        return s;
    });
    // changed only by snapshotter
    private final long[] globalBuckets = new long[256];
    private long globalCount = 0;
    private long globalSum = 0;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax = 0;


    @Override
    public void record(long value) {
        ThreadBuffers my = myBuffers.get();
        int currBuff = -1;
        while (true) {
            currBuff = active;
            my.inside.set(currBuff);
            if (active == currBuff) { // step 3 check
                break;
            }
            my.inside.set(-1);
        }
//        replace while above with lines below to remove step 3 recheck.
//        currBuff = active;
//        my.inside.set(currBuff);

        int bucketIndex = (int) Math.min(value / 4, 255);
        my.buckets[currBuff][bucketIndex]++;
        my.count[currBuff]++;
        my.sum[currBuff] += value;
        my.min[currBuff] = Math.min(my.min[currBuff], value);
        my.max[currBuff] = Math.max(my.max[currBuff], value);
        my.inside.setRelease(-1);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
        int old = active;
        active = 1 - old;
        for (ThreadBuffers s : allStates) { //waiting for writers to finish
            while (s.inside.get() == old){
                Thread.onSpinWait();
            }
            for(int i = 0; i < BUCKET_COUNT; i++){
                globalBuckets[i] += s.buckets[old][i];
            }
            globalCount += s.count[old];
            globalSum += s.sum[old];
            globalMin = Math.min(globalMin, s.min[old]);
            globalMax = Math.max(globalMax, s.max[old]);

            Arrays.fill(s.buckets[old], 0);
            s.count[old] = 0;
            s.sum[old] = 0;
            s.min[old] = Long.MAX_VALUE;
            s.max[old] = 0;
        }

        long minToReport = (globalCount == 0) ? 0 : globalMin;
        long p50 = percentile(globalBuckets, globalCount, 50);
        long p99 = percentile(globalBuckets, globalCount, 99);
        return new Snapshot(
                Arrays.copyOf(globalBuckets, globalBuckets.length),
                globalCount,
                globalSum,
                minToReport,
                globalMax,
                p50,
                p99);
        }
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

    static final class ThreadBuffers {
    // Два буфера: [0] и [1]. Обычные массивы и переменные!
    final long[][] buckets = new long[2][256];
    final long[] count = new long[2];
    final long[] sum = new long[2];
    final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
    final long[] max = {0, 0};

    // Флаг входа: -1 = вне буферов, 0 = запись в буфер 0, 1 = запись в буфер 1
    final AtomicInteger inside = new AtomicInteger(-1);
}
}
