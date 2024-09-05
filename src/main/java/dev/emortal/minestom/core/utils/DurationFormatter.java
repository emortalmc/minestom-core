package dev.emortal.minestom.core.utils;

import net.minestom.server.utils.time.TimeUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.StringJoiner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DurationFormatter {
    private static final Duration YEAR_DURATION = Duration.ofDays(365);

    public static @NotNull String ofGreatestUnit(@NotNull Duration duration) {
        if (duration.compareTo(YEAR_DURATION) > -1) return duration.toDays() + "y";
        if (duration.compareTo(TimeUnit.DAY.getDuration()) > -1) return duration.toDays() + "d";
        if (duration.compareTo(TimeUnit.HOUR.getDuration()) > -1) return duration.toHours() + "hr";
        if (duration.compareTo(TimeUnit.MINUTE.getDuration()) > -1) return duration.toMinutes() + "min";
        if (duration.compareTo(TimeUnit.SECOND.getDuration()) > -1) return duration.toSeconds() + "s";
        if (duration.compareTo(TimeUnit.MILLISECOND.getDuration()) > -1) return duration.toMillis() + "ms";
        return duration.toNanosPart() + "ns";
    }

    public static @NotNull String ofGreatestUnits(@NotNull Duration duration, int unitCount, @Nullable ChronoUnit smallestUnit) {
        StringJoiner builder = new StringJoiner(", ");

        long days = duration.toDaysPart();
        long years = days / 365;
        days %= 365;

        if (years > 0) {
            builder.add(years + "y");
            if (--unitCount == 0) return builder.toString();
        }
        if (smallestUnit == ChronoUnit.YEARS) return builder.toString();

        if (days > 0) {
            builder.add(days + "d");
            if (--unitCount == 0) return builder.toString();
        }
        if (smallestUnit == ChronoUnit.DAYS) return builder.toString();

        long hours = duration.toHoursPart();
        if (hours > 0) {
            builder.add(hours + "hr");
            if (--unitCount == 0) return builder.toString();
        }
        if (smallestUnit == ChronoUnit.HOURS) return builder.toString();

        long minutes = duration.toMinutesPart();
        if (minutes > 0) {
            builder.add(minutes + "min");
            if (--unitCount == 0) return builder.toString();
        }
        if (smallestUnit == ChronoUnit.MINUTES) return builder.toString();

        long seconds = duration.toSecondsPart();
        if (seconds > 0) {
            builder.add(seconds + "s");
            if (--unitCount == 0) return builder.toString();
        }
        if (smallestUnit == ChronoUnit.SECONDS) return builder.toString();

        long millis = duration.toMillisPart();
        if (millis > 0) {
            builder.add(millis + "ms");
            if (--unitCount == 0) return builder.toString();
        }
        if (smallestUnit == ChronoUnit.MILLIS) return builder.toString();

        long nanos = duration.toNanosPart();
        if (nanos > 0) {
            builder.add(nanos + "ns");
        }

        return builder.toString();
    }

    private DurationFormatter() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}