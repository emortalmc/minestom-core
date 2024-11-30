package dev.emortal.minestom.core.module.core.playerprovider;

import dev.emortal.minestom.core.module.permissions.Permission;
import dev.emortal.minestom.core.module.permissions.PermissionHolder;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class EmortalPlayerImpl extends Player implements EmortalPlayer {
    private final @NotNull Set<Permission> permissions = new CopyOnWriteArraySet<>();

    public EmortalPlayerImpl(@NotNull PlayerConnection playerConnection, @NotNull GameProfile gameProfile) {
        super(playerConnection, gameProfile);
    }

    @Override
    public @NotNull Set<Permission> getPermissions() {
        return this.permissions;
    }
}
