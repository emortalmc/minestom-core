package dev.emortal.minestom.core.utils.resolver;

import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.service.mcplayer.McPlayerService;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import net.minestom.server.entity.Player;
import net.minestom.server.network.ConnectionManager;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public final class PlayerResolver {

    private final @Nullable McPlayerService playerService;
    private final @NotNull ConnectionManager connectionManager; // TODO: Replace with an abstraction for testing

    public PlayerResolver(@Nullable McPlayerService playerService, @NotNull ConnectionManager connectionManager) {
        this.playerService = playerService;
        this.connectionManager = connectionManager;
    }

    @Blocking
    public @Nullable LocalMcPlayer getPlayer(@NotNull UUID uuid) throws StatusException {
        Player player = this.connectionManager.getPlayer(uuid);
        if (player != null) return this.convertPlayer(player);

        return this.requestPlayer(uuid);
    }

    @Blocking
    public @Nullable LocalMcPlayer getPlayer(@NotNull String username) throws StatusException {
        String usernameLowercase = username.toLowerCase(Locale.ROOT);

        Player player = this.connectionManager.getPlayer(usernameLowercase);
        if (player != null) return this.convertPlayer(player);

        return this.requestPlayer(usernameLowercase);
    }

    @Blocking
    private @Nullable LocalMcPlayer requestPlayer(@NotNull UUID uuid) throws StatusException {
        if (this.playerService == null) return null;

        McPlayer player;
        try {
            player = this.playerService.getPlayerById(uuid);
        } catch (StatusRuntimeException exception) {
            throw new StatusException(exception.getStatus(), exception.getTrailers());
        }

        return player != null ? this.convertPlayer(player) : null;
    }

    @Blocking
    private @Nullable LocalMcPlayer requestPlayer(@NotNull String username) throws StatusException {
        if (this.playerService == null) return null;

        McPlayer player;
        try {
            player = this.playerService.getPlayerByUsername(username);
        } catch (StatusRuntimeException exception) {
            throw new StatusException(exception.getStatus(), exception.getTrailers());
        }

        return player != null ? this.convertPlayer(player) : null;
    }

    private @NotNull LocalMcPlayer convertPlayer(@NotNull Player player) {
        return new LocalMcPlayer(player.getUuid(), player.getUsername(), player.isOnline());
    }

    private @NotNull LocalMcPlayer convertPlayer(@NotNull McPlayer player) {
        return new LocalMcPlayer(UUID.fromString(player.getId()), player.getCurrentUsername(), player.hasCurrentServer());
    }
}
