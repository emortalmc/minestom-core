package dev.emortal.minestom.core.utils;

import java.util.ArrayDeque;

public class RollingAverage {
    private final ArrayDeque<Double> queue;
    private final int size;
    private double sum = 0.0;

    public RollingAverage(int size) {
        this.queue = new ArrayDeque<>(size);
        this.size = size;
    }

    public int sampleCount() {
        synchronized (this) {
            return this.queue.size();
        }
    }

    public void addSample(double sample) {
        synchronized (this) {
            this.sum += sample;
            this.queue.addLast(sample);
            if (this.queue.size() > this.size) {
                this.sum -= this.queue.removeFirst();
            }
        }
    }

    public double mean() {
        return this.sum / this.queue.size();
    }

    public double min() {
        synchronized (this) {
            return this.queue.stream().min(Double::compareTo).orElse(0.0);
        }
    }

    public double max() {
        synchronized (this) {
            return this.queue.stream().max(Double::compareTo).orElse(0.0);
        }
    }

    /**
     * Returns the value at the given percentile.
     *
     * @param percentile The percentile to get the value for, between 0 and 1.
     * @return The value at the given percentile.
     */
    public double percentile(double percentile) {
        synchronized (this) {
            return this.queue.stream().sorted().skip((int) (this.queue.size() * percentile)).findFirst().orElse(0.0);
        }
    }

}
