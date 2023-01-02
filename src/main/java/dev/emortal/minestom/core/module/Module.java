package dev.emortal.minestom.core.module;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

public abstract class Module {
    protected final @NotNull ModuleManager moduleManager;
    protected final @NotNull EventNode<Event> eventNode;

    protected Module(@NotNull ModuleEnvironment environment) {
        this.moduleManager = environment.moduleManager();
        this.eventNode = environment.eventNode();
    }

    public abstract boolean onLoad();

    public abstract void onUnload();

    /**
     * called when the server is ready to accept connections
     * (MinecraftServer#start has been called)
     */
    public void onReady() {
    }

    public @NotNull EventNode<Event> getEventNode() {
        return this.eventNode;
    }
}
