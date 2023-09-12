package dev.emortal.minestom.core;

// We have this as a separate class so that things that build on to MinestomServer can call it to initialise logging earlier
public final class LoggingInitializer {

    private static volatile boolean initialized;

    public static void initialize() {
        if (initialized) return;

        String loggerConfigFile = Environment.isProduction() ? "logback-prod.xml" : "logback-dev.xml";
        System.setProperty("logback.configurationFile", loggerConfigFile);

        initialized = true;
    }

    private LoggingInitializer() {
    }
}
