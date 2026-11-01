package org.main.benchmark;

import java.util.Random;

public class ValuesGenerator {
    public static final int SIZE = 1 << 20; // 2^20
    public static final int MAX_VALUE = 1023; // k in [1..1023]
    public static final double S = 1.15;
    public static final long DEFAULT_SEED = 1L;

    public static long[] generateValues() {
        return generateValues(SIZE, DEFAULT_SEED);
    }

    public static long[] generateValues(int size, long seed) {
        double[] cdf = new double[MAX_VALUE];
        double totalWeight = 0.0;
        for (int k = 1; k <= MAX_VALUE; k++) {
            totalWeight += 1.0 / Math.pow(k, S);
        }

        // 2. Строим кумулятивную функцию распределения (CDF)
        double cumulative = 0.0;
        for (int i = 0; i < MAX_VALUE; i++) {
            int k = i + 1;
            cumulative += (1.0 / Math.pow(k, S)) / totalWeight;
            cdf[i] = cumulative;
        }
        cdf[MAX_VALUE - 1] = 1.0;

        long[] values = new long[size];
        Random random = new Random(seed);

        for (int i = 0; i < size; i++) {
            double u = random.nextDouble();
            values[i] = sampleZipf(cdf, u);
        }

        return values;
    }

    /**
     * Бинарный поиск значения k по случайному числу u в диапазоне [0, 1).
     * Возвращает k от 1 до MAX_VALUE (1023).
     */
    private static int sampleZipf(double[] cdf, double u) {
        int low = 0;
        int high = cdf.length - 1;

        while (low < high) {
            int mid = (low + high) >>> 1;
            if (u > cdf[mid]) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low + 1;
    }
}
