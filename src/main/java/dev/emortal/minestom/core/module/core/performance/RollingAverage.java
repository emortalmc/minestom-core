package dev.emortal.minestom.core.module.core.performance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;

public final class RollingAverage {

    private final Queue<BigDecimal> samples;
    private final int size;
    private BigDecimal sum = BigDecimal.ZERO;

    public RollingAverage(int size) {
        this.samples = new ArrayDeque<>(size);
        this.size = size;
    }

    public int sampleCount() {
        synchronized (this) {
            return samples.size();
        }
    }

    public void addSample(@NotNull BigDecimal sample) {
        synchronized (this) {
            sum = sum.add(sample);
            samples.add(sample);
            if (samples.size() > size) {
                // Remove old samples from the queue and sum so we only keep max. size samples
                sum = sum.subtract(samples.remove());
            }
        }
    }

    public double mean() {
        synchronized (this) {
            if (samples.isEmpty()) return 0;
            final BigDecimal divisor = BigDecimal.valueOf(samples.size());
            return sum.divide(divisor, 30, RoundingMode.HALF_UP).doubleValue();
        }
    }

    public double min() {
        synchronized (this) {
            BigDecimal min = null;
            for (final BigDecimal sample : samples) {
                if (min == null | sample.compareTo(min) < 0) min = sample;
            }
            return min == null ? 0 : min.doubleValue();
        }
    }

    public double max() {
        synchronized (this) {
            BigDecimal max = null;
            for (final BigDecimal sample : samples) {
                if (max == null | sample.compareTo(max) > 0) max = sample;
            }
            return max == null ? 0 : max.doubleValue();
        }
    }

    /**
     * Returns the value at the given percentile.
     *
     * @param percentile The percentile to get the value for, between 0 and 1.
     * @return The value at the given percentile.
     */
    public double percentile(double percentile) {
        if (percentile < 0 || percentile > 1) throw new IllegalArgumentException("Percentile must be between 0 and 1!");

        final BigDecimal[] sortedSamples;
        synchronized (this) {
            if (samples.isEmpty()) return 0;
            sortedSamples = samples.toArray(new BigDecimal[0]);
        }
        Arrays.sort(sortedSamples);

        final int rank = (int) Math.ceil(percentile * (sortedSamples.length - 1));
        return sortedSamples[rank].doubleValue();
    }
}
