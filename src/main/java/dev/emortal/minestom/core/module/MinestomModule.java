package dev.emortal.minestom.core.module;

import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.env.ModuleEnvironment;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

public abstract class MinestomModule extends Module {

    protected final @NotNull EventNode<Event> eventNode;

    protected MinestomModule(@NotNull ModuleEnvironment environment) {
        super(environment);

        this.eventNode = EventNode.all(environment.data().name());
        MinecraftServer.getGlobalEventHandler().addChild(this.eventNode);
    }

    /**
     * called when the server is ready to accept connections
     * (MinecraftServer#start has been called)
     */
    public void onReady() {
    }
}
