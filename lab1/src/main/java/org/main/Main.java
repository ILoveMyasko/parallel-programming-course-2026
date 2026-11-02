package org.main;


import org.main.benchmark.Benchmark;
import org.main.benchmark.SnapshotTest;
import org.main.benchmark.ValuesGenerator;
import org.main.collector.*;

public class Main {
    static void main() {
        long[] values = ValuesGenerator.generateValues();
        Benchmark benchmark = new Benchmark();
        try {
//            System.out.println(benchmark.measurePoint(new SingleThreadedCollector(), values, 1)); // этап 0
//
//            benchmark.measureAll("Этап 1 - Synchronized", SynchronizedCollector::new, values);
            benchmark.measureAll("Этап 1 - Empty Synchronized", EmptyLockCollector::new, values);
//
//            benchmark.measureAll("Этап 2 - Sharded", ShardedLockCollector::new, values);
//            SnapshotTest test = new SnapshotTest();
//            test.runTest(new ShardedLockCollector(), values);

//            SnapshotTest test = new SnapshotTest();
//            test.runTest(new ThreadLocalCollector(), values);
//            benchmark.measureAll("Этап 3 - ThreadLocal", ThreadLocalCollector::new, values);

//            SnapshotTest test = new SnapshotTest();
//            test.runTest(new DoubleBufferedCollector(), values);
//            benchmark.measureAll("Этап 4 - DoubleBuffered", DoubleBufferedCollector::new, values);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
