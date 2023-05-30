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

public class MinestomMetrics implements MeterBinder {
    private final @NotNull ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final @NotNull AtomicInteger ticks = new AtomicInteger();

    private final @NotNull EventNode<Event> eventNode;

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

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            List<MultiGauge.Row<?>> list = new ArrayList<>();
            for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
                MultiGauge.Row<Number> of = MultiGauge.Row.of(Tags.of("instance.id", instance.getUniqueId().toString()), instance.getChunks().size());
                list.add(of);
            }
            chunkGauge.register(list);
        }).repeat(5, ChronoUnit.SECONDS).delay(TaskSchedule.nextTick()).schedule();

        MultiGauge entityGauge = MultiGauge.builder("minestom.entities")
                .description("The amount of entities currently loaded per instance")
                .baseUnit("entities")
                .register(registry);

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            List<MultiGauge.Row<?>> list = new ArrayList<>();
            for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
                MultiGauge.Row<Number> of = MultiGauge.Row.of(Tags.of("instance.id", instance.getUniqueId().toString()), instance.getEntities().size());
                list.add(of);
            }
            entityGauge.register(list);
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

        this.scheduler.scheduleAtFixedRate(() -> {
            int ticks = this.ticks.getAndSet(0);
            tickPerSecond.record(ticks);
        }, 1, 1, TimeUnit.SECONDS);
    }
}
