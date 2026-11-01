package org.main.benchmark;

import org.main.collector.MetricsCollector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class Benchmark {
    private final int MEASUREMENT_COUNT = 5;
    volatile boolean stop;

    public double run(MetricsCollector collector, long[] values, int threadCount, int seconds) throws InterruptedException {
        stop = false;
        CountDownLatch startSignal = new CountDownLatch(1);
        long[] results = new long[threadCount];
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
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

        long startTime = System.nanoTime();
        startSignal.countDown();
        Thread.sleep(seconds * 1000L);
        stop = true;
        long endTime = System.nanoTime();
        for (Thread t : threads) {
            t.join();
        }
        long sumResults = Arrays.stream(results).sum();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        return sumResults / durationSeconds;
    }

    public double measurePoint(MetricsCollector collector, long[] values, int threadCount) throws InterruptedException {
        run(collector, values, threadCount, 5); // ПРОГРЕВ: 5 сек крутим вхолостую, результат выбрасываем
        List<Double> results = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_COUNT; i++) { // 5 честных забегов по 5 секунд
            results.add(
                    run(collector, values, threadCount, 5)
            );
        }
        double median = results.stream()
                .sorted()
                .toList()
                .get(MEASUREMENT_COUNT / 2);

        System.out.println(" (snapshot count: " + collector.snapshot().count() + ")"); // защита от выкидывания кода JIT
        return median;
    }

    public Map<Integer, Double> measureAll(String name, Supplier<MetricsCollector> supplier, long[] values)
            throws InterruptedException {
        int[] threadCounts = {1, 2, 4, 8, 16};
        return measureAll(name, supplier, values, threadCounts);
    }

    public Map<Integer, Double> measureAll(String name,
                                           Supplier<MetricsCollector> supplier,
                                           long[] values,
                                           int[] threadCounts) throws InterruptedException {

        Map<Integer, Double> results = new LinkedHashMap<>();

        for (int t : threadCounts) {
            MetricsCollector collector = supplier.get();
            double opsPerSec = measurePoint(collector, values, t);
            double mops = opsPerSec / 1_000_000.0;
            results.put(t, mops);

        }
        System.out.println("Итоги для таблицы (" + name + "):");
        results.forEach((threads, mops) -> System.out.printf("T = %2d | %6.2f Mops/s\n", threads, mops));
        System.out.println("==========================================\n");
        return results;
    }
}
