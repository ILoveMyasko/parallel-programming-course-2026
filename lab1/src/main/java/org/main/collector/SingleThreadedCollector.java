package org.main.collector;

import java.util.Arrays;

public class SingleThreadedCollector implements MetricsCollector{

    private final int BUCKET_COUNT = 256;
    private long[] buckets;
    private long count;
    private long sum;
    private long min;
    private long max;

    public SingleThreadedCollector(){
        this.buckets = new long[256];
        this.count = 0;
        this.sum = 0;
        this.max = Long.MIN_VALUE;
        this.min = Long.MAX_VALUE;
    }

    @Override
    public void record(long value) {
        int bucketIndex = (int) Math.min(value / 4, 255); // value >= 1024 ? 255 : (int)(value >> 2);
        buckets[bucketIndex]++;
        count++;
        sum += value;
        max = Math.max(value, max);
        min = Math.min(value, min);
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(
                Arrays.copyOf(buckets, buckets.length),
                count,
                sum,
                min,
                max,
                percentile(50),
                percentile(99)
        );
    }

    public long percentile(int percentile){
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
