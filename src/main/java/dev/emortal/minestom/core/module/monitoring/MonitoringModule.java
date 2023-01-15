package dev.emortal.minestom.core.module.monitoring;

import com.sun.net.httpserver.HttpServer;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

@ModuleData(name = "monitoring", required = false)
public class MonitoringModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringModule.class);
    private final @NotNull String serviceName;

    public MonitoringModule(@NotNull ModuleEnvironment environment, @NotNull String serviceName) {
        super(environment);
        this.serviceName = serviceName;
    }

    @Override
    public boolean onLoad() {
        String envEnabled = System.getenv("MONITORING_ENABLED");
        if (!(Environment.isProduction() || Boolean.parseBoolean(envEnabled))) {
            LOGGER.info("Monitoring is disabled (production: {}, env: {})", Environment.isProduction(), envEnabled);
            return false;
        }

        LOGGER.info("Starting monitoring with: [serviceName={}, server={}]", this.serviceName, Environment.getHostname());
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.config()
                .meterFilter(new PrometheusRenameFilter())
                .commonTags("service", this.serviceName);

        if (Environment.isProduction()) registry.config().commonTags("server", Environment.getHostname());

        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new MinestomMetrics(this.eventNode).bindTo(registry);

        // Add the registry globally so that it can be used by other modules without having to pass it around
        Metrics.addRegistry(registry);

        try {
            LOGGER.info("Starting Prometheus HTTP server on port 8081");
            HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
            server.createContext("/metrics", exchange -> {
                String response = registry.scrape();
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                exchange.close();
            });

            new Thread(server::start, "micrometer-http").start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public void onUnload() {

    }
}
