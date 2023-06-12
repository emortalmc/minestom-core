package dev.emortal.minestom.core.module.core.performance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

/**
 * This code is taken from Paper, licensed under the MIT license.
 *
 * <p>
 * Original source: https://github.com/PaperMC/Paper/blob/master/patches/server/0031-Further-improve-server-tick-loop.patch
 * </p>
 */
public final class TpsRollingAverage {
    private static final long SECONDS_IN_NANO = 1_000_000_000L;
    private static final int TPS = MinecraftServer.TICK_PER_SECOND;

    private final int size;
    private long time;
    private BigDecimal total;
    private int index;
    private final BigDecimal[] samples;
    private final long[] times;

    public TpsRollingAverage(int size) {
        this.size = size;
        time = size * SECONDS_IN_NANO;
        total = new BigDecimal(TPS).multiply(new BigDecimal(SECONDS_IN_NANO)).multiply(new BigDecimal(size));
        samples = new BigDecimal[size];
        times = new long[size];
        for (int i = 0; i < size; i++) {
            samples[i] = new BigDecimal(TPS);
            times[i] = SECONDS_IN_NANO;
        }
    }

    public void addSample(@NotNull BigDecimal sample, long time, @NotNull BigDecimal total) {
        this.time -= times[index];
        this.total = this.total.subtract(samples[index].multiply(new BigDecimal(times[index])));
        samples[index] = sample;
        times[index] = time;
        this.time += time;
        this.total = this.total.add(total);
        if (++index == size) index = 0;
    }

    public double average() {
        return total.divide(new BigDecimal(time), 30, RoundingMode.HALF_UP).doubleValue();
    }
}
