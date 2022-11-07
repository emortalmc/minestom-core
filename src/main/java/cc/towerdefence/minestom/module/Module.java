package cc.towerdefence.minestom.module;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class Module {
    protected final @NotNull EventNode<Event> eventNode;

    protected Module(@NotNull EventNode<Event> eventNode) {
        this.eventNode = eventNode;
    }

    @ApiStatus.Internal
    public abstract boolean onLoad();

    @ApiStatus.Internal
    public abstract void onUnload();

    /**
     * called when the server is ready to accept connections
     * (MinecraftServer#start has been called)
     */
    public void onReady() {
    }

}
