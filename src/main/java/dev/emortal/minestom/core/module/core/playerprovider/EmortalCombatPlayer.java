package dev.emortal.minestom.core.module.core.playerprovider;

import dev.emortal.minestom.core.module.permissions.Permission;
import io.github.togar2.pvp.player.CombatPlayerImpl;
import net.minestom.server.network.player.PlayerConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

// This class should be instantiated instead of CombatPlayerImpl by any games that use the Emortal API
@SuppressWarnings("unused")
public class EmortalCombatPlayer extends CombatPlayerImpl implements EmortalPlayer {
    private final @NotNull Set<Permission> permissions = new CopyOnWriteArraySet<>();

    public EmortalCombatPlayer(@NotNull UUID uuid, @NotNull String username, @NotNull PlayerConnection playerConnection) {
        super(uuid, username, playerConnection);
    }

    @Override
    public @NotNull Set<Permission> getPermissions() {
        return this.permissions;
    }
}
