package dev.emortal.minestom.core.module.monitoring;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.monitoring.TickMonitor;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MinestomMetrics implements MeterBinder {

    private final @NotNull EventNode<Event> eventNode;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger ticks = new AtomicInteger();

    public MinestomMetrics(@NotNull EventNode<Event> eventNode) {
        this.eventNode = eventNode;
    }

    @Override
    public void bindTo(@NotNull MeterRegistry registry) {
        Gauge.builder("minestom.players", MinecraftServer.getConnectionManager().getOnlinePlayers(), Collection::size)
                .description("The amount of players currently online")
                .register(registry);

        Gauge.builder("minestom.instances", () -> MinecraftServer.getInstanceManager().getInstances().size())
                .description("The amount of instances currently loaded")
                .register(registry);

        MultiGauge chunkGauge = MultiGauge.builder("minestom.chunks")
                .description("The amount of chunks currently loaded per instance")
                .baseUnit("chunks")
                .register(registry);

        MultiGauge entityGauge = MultiGauge.builder("minestom.entities")
                .description("The amount of entities currently loaded per instance")
                .baseUnit("entities")
                .register(registry);

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            List<MultiGauge.Row<?>> chunkRows = new ArrayList<>();
            List<MultiGauge.Row<?>> entityRows = new ArrayList<>();

            for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
                var tags = Tags.of("instance.id", instance.getUuid().toString());

                chunkRows.add(MultiGauge.Row.of(tags, instance.getChunks().size()));
                entityRows.add(MultiGauge.Row.of(tags, instance.getEntities().size()));
            }

            chunkGauge.register(chunkRows, true);
            entityGauge.register(entityRows, true);
        }).repeat(5, ChronoUnit.SECONDS).delay(TaskSchedule.nextTick()).schedule();

        Timer tickTimer = Timer.builder("minestom.tick.time")
                .description("The time taken to process a tick, commonly referred to as MSPT")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        this.eventNode.addListener(ServerTickMonitorEvent.class, event -> {
            TickMonitor monitor = event.getTickMonitor();

            // Multiply by 1,000,000 to convert ms -> ns and then convert to a long
            tickTimer.record((long) (monitor.getTickTime() * 1E6), TimeUnit.NANOSECONDS);
            this.ticks.incrementAndGet();
        });

        DistributionSummary tickPerSecond = DistributionSummary.builder("minestom.tick.per_second")
                .description("The amount of ticks per second")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .baseUnit("ticks")
                .register(registry);

        this.scheduler.scheduleAtFixedRate(() -> tickPerSecond.record(this.ticks.getAndSet(0)), 1, 1, TimeUnit.SECONDS);
    }
}
