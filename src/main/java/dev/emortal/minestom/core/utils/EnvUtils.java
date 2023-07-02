package dev.emortal.minestom.core.utils;

import dev.emortal.minestom.core.Environment;
import org.jetbrains.annotations.NotNull;

public final class EnvUtils {

    public static @NotNull String getOrDefault(@NotNull String envKey, @NotNull String defaultValue) {
        String envValue = System.getenv(envKey);
        return envValue == null || envValue.isEmpty() ? defaultValue : envValue;
    }

    /**
     * @param envKey       the environment variable key
     * @param defaultValue the default value to return if the environment variable is not set
     * @return the environment variable value or the default value
     * @throws IllegalStateException if the environment variable is not set and the environment is production
     */
    public static @NotNull String getOrDefaultUnlessProd(@NotNull String envKey, @NotNull String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) return envValue;

        if (Environment.isProduction()) {
            throw new IllegalStateException("Environment variable " + envKey + " is not set");
        }

        return defaultValue;
    }

    private EnvUtils() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}
