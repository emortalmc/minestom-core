package dev.emortal.minestom.core.module.core;

import dev.emortal.api.utils.resolvers.PlayerResolver;
import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.core.badge.BadgeAdminCommand;
import dev.emortal.minestom.core.module.core.badge.BadgeCommand;
import dev.emortal.minestom.core.module.core.command.PerformanceCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "core", required = false)
public final class CoreModule extends Module {

    public CoreModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        PlayerResolver.setPlatformUsernameResolver(username -> {
            Player player = MinecraftServer.getConnectionManager().getPlayer(username);
            if (player != null) return new PlayerResolver.CachedMcPlayer(player.getUuid(), player.getUsername(), player.isOnline());
            return null;
        });

        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new PerformanceCommand(this.eventNode));
        commandManager.register(new BadgeCommand());
        commandManager.register(new BadgeAdminCommand());

        return true;
    }

    @Override
    public void onUnload() {
    }
}
