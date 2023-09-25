package dev.emortal.minestom.core.module.monitoring;

import com.sun.net.httpserver.HttpServer;
import dev.emortal.api.modules.annotation.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.MinestomModule;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.labels.Pyroscope;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;

@ModuleData(name = "monitoring")
public final class MonitoringModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringModule.class);

    private static final boolean ENABLED = Boolean.parseBoolean(System.getenv("MONITORING_ENABLED"));
    private static final String FLEET_NAME = Objects.requireNonNullElse(System.getenv("FLEET_NAME"), "unknown");
    private static final String PYROSCOPE_SERVER_ADDRESS = System.getenv("PYROSCOPE_SERVER_ADDRESS");

    public MonitoringModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        if (!Environment.isProduction() || !ENABLED) {
            LOGGER.warn("Monitoring is disabled.");
            return false;
        }

        LOGGER.info("Starting monitoring with: [fleet={}, server={}]", FLEET_NAME, Environment.getHostname());

        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.config().meterFilter(new PrometheusRenameFilter()).commonTags("fleet", FLEET_NAME);

        if (Environment.isProduction()) {
            registry.config().commonTags("server", Environment.getHostname());
        }

        if (Environment.isProduction()) {
            this.setupPyroscope();
        }

        // Java
        new ClassLoaderMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        // Proc
        new ProcessorMetrics().bindTo(registry);
        // Custom
        new MinestomMetrics(this.eventNode).bindTo(registry);
        new MinestomPacketMetrics(this.eventNode).bindTo(registry);

        // Add the registry globally so that it can be used by other modules without having to pass it around
        Metrics.addRegistry(registry);

        try {
            LOGGER.info("Starting Prometheus HTTP server on port 8081");
            var server = HttpServer.create(new InetSocketAddress(8081), 0);

            server.createContext("/metrics", exchange -> {
                String response = registry.scrape();
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(response.getBytes());
                }
                exchange.close();
            });

            new Thread(server::start, "micrometer-http").start();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        return true;
    }

    private void setupPyroscope() {
        if (PYROSCOPE_SERVER_ADDRESS == null) {
            LOGGER.warn("PYROSCOPE_SERVER_ADDRESS not set. Pyroscope will not be enabled.");
            return;
        }

        Pyroscope.setStaticLabels(Map.of(
                "fleet", FLEET_NAME,
                "pod", Environment.getHostname()
        ));

        Config config = new Config.Builder()
                .setApplicationName(FLEET_NAME)
                .setProfilingEvent(EventType.ITIMER)
                .setFormat(Format.JFR)
                .setServerAddress(PYROSCOPE_SERVER_ADDRESS)
                .build();
        PyroscopeAgent.start(new PyroscopeAgent.Options.Builder(config).build());
    }

    @Override
    public void onUnload() {
    }
}
