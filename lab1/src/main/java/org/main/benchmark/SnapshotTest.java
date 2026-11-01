package org.main.benchmark;

import org.main.collector.MetricsCollector;
import org.main.collector.Snapshot;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class SnapshotTest {

    private final int WRITER_COUNT = 4;
    private final int SNAPSHOT_COUNT = 10000;
    private volatile boolean stop = false;

    public void runTest(MetricsCollector collector, long[] values) throws InterruptedException {
        stop = false;
        Thread[] threads = new Thread[WRITER_COUNT];
        CountDownLatch startSignal = new CountDownLatch(1);
        long[] results = new long[WRITER_COUNT];
        for (int i = 0; i < WRITER_COUNT; i++) {
            int threadNumber = i;
            threads[i] = new Thread(() -> {
                long local_count = 0;
                try {
                    int startingPoint = (threadNumber * 1000) % values.length;
                    startSignal.await();
                    while (!stop) {
                        collector.record(values[startingPoint]);
                        local_count++;
                        startingPoint++;
                        if (startingPoint == values.length) startingPoint = 0;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                results[threadNumber] = local_count;
            });
            threads[i].start();
        }
        startSignal.countDown();
        int inconsistentCount = 0;
        int sumLessThanCount = 0;
        int sumMoreThanCount = 0;
        for (int i = 0; i < SNAPSHOT_COUNT; i++) {
            Snapshot snapshot = collector.snapshot();
            long sumBuckets = Arrays.stream(snapshot.buckets()).sum();
            if (snapshot.count() != sumBuckets){
                inconsistentCount++;
                if (snapshot.count() > sumBuckets){
                    sumLessThanCount++;
                }
                else sumMoreThanCount++;

            }
        }
        stop = true;
        for (Thread t : threads) {
            t.join();
        }

        long totalWritersOps = Arrays.stream(results).sum();
        long finalCount = collector.snapshot().count();
        long finalDiff = finalCount - totalWritersOps;

        double brokenPercent = (double) inconsistentCount / SNAPSHOT_COUNT * 100.0;

        // Печатаем аккуратный отчёт для таблицы в README
        System.out.println("==========================================================================================");
        System.out.println("РЕЗУЛЬТАТЫ СТРЕСС-ТЕСТА СОГЛАСОВАННОСТИ: ");
        System.out.println("==========================================================================================");
        System.out.printf("Всего снимков:          %d\n", SNAPSHOT_COUNT);
        System.out.printf("Доля битых снимков:     %.2f%% (%d из %d)\n", brokenPercent, inconsistentCount, SNAPSHOT_COUNT);
        System.out.printf("Снимок: sum < count:    %d раз\n", sumLessThanCount);
        System.out.printf("Снимок: sum > count:    %d раз\n", sumMoreThanCount);
        System.out.printf("Реально вызовов:        %d\n", totalWritersOps);
        System.out.printf("Итоговый count:         %d\n", finalCount);
        System.out.printf("Итоговый diff (count - вызовы): %d\n", finalDiff);
        System.out.println("==========================================================================================\n");

    }

}
