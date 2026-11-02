package org.main.collector;


import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class ShardedLockCollector implements MetricsCollector{

    private final int BUCKET_COUNT = 256;
    private final int LOCK_COUNT = 16;
    private long[] buckets;
    private final Object[] locks = new Object[LOCK_COUNT];
    private AtomicLong count;
    private AtomicLong sum;
    private AtomicLong min;
    private AtomicLong max;

     public ShardedLockCollector(){
        this.buckets = new long[BUCKET_COUNT];
        for (int i = 0; i < LOCK_COUNT; i++) {
            this.locks[i] = new Object();
        }
        this.count = new AtomicLong(0);
        this.sum = new AtomicLong(0);
        this.max = new AtomicLong(Long.MIN_VALUE);
        this.min = new AtomicLong(Long.MAX_VALUE);
    }

    @Override
    public void record(long value) {
        int bucketIndex = (int) Math.min(value / 4, BUCKET_COUNT - 1); // value >= 1024 ? 255 : (int)(value >> 2);
        synchronized (locks[bucketIndex % LOCK_COUNT]) {
            buckets[bucketIndex]++;
        }
        // between these lines we can get torn snapshot where sum > count (4 out of 10000)?
        // but usually we will get sum < count because we have already made multiple increments of count even with 1 thread
        count.incrementAndGet();
        sum.addAndGet(value);

        //CAS
        long currentMax = max.get();
        if (value > currentMax) {
            while (!max.compareAndSet(currentMax, value)) {
                currentMax = max.get();
                if (value <= currentMax) break;
            }
        }
        long currentMin = min.get();
         if (value < currentMin) {
            while (!min.compareAndSet(currentMin, value)) {
                currentMin = min.get();
                if (value >= currentMin) break;
            }
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] copyBuckets = new long[BUCKET_COUNT];
        for (int s = 0; s < LOCK_COUNT; s++) { // 0, 16, 32 ...
            synchronized (locks[s]) {
                for (int b = s; b < BUCKET_COUNT; b += LOCK_COUNT) {
                    copyBuckets[b] = buckets[b];
                }
            }
        }
        long c = count.get();
        long s = sum.get();
        long currentMin = min.get();
        long currentMax = max.get();
        return new Snapshot(
                copyBuckets,
                c,
                s,
                currentMin,
                currentMax,
                percentile(copyBuckets, c, 50),
                percentile(copyBuckets, c, 99)
        );
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
}
