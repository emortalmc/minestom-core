package dev.emortal.minestom.core.utils;

import net.minestom.server.utils.time.TimeUnit;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public final class DurationFormatter {

    public static String ofGreatestUnit(@NotNull Duration duration) {
        if (duration.compareTo(TimeUnit.DAY.getDuration()) > -1) return duration.toDays() + "d";
        if (duration.compareTo(TimeUnit.HOUR.getDuration()) > -1) return duration.toHours() + "hr";
        if (duration.compareTo(TimeUnit.MINUTE.getDuration()) > -1) return duration.toMinutes() + "min";
        if (duration.compareTo(TimeUnit.SECOND.getDuration()) > -1) return duration.toSeconds() + "s";
        if (duration.compareTo(TimeUnit.MILLISECOND.getDuration()) > -1) return duration.toMillis() + "ms";
        return duration.toNanosPart() + "ns";
    }

    private DurationFormatter() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}