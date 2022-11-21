package cc.towerdefence.minestom.module.core;

import cc.towerdefence.api.utils.resolvers.PlayerResolver;
import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.ModuleEnvironment;
import cc.towerdefence.minestom.module.core.command.PerformanceCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "core", required = false)
public class CoreModule extends Module {

    public CoreModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        MinecraftServer.getCommandManager().register(new PerformanceCommand(this.eventNode));

        PlayerResolver.setPlatformUsernameResolver(username -> {
            Player player = MinecraftServer.getConnectionManager().getPlayer(username);
            if (player != null) return new PlayerResolver.CachedMcPlayer(player.getUuid(), player.getUsername());
            return null;
        });
        return true;
    }

    @Override
    public void onUnload() {

    }
}
