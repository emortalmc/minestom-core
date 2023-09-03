package dev.emortal.minestom.core.utils.command.argument;

import dev.emortal.api.grpc.mcplayer.McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod;
import dev.emortal.api.model.common.Pageable;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.minestom.core.utils.resolver.LocalMcPlayer;
import dev.emortal.minestom.core.utils.resolver.PlayerResolver;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.Suggestion;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

public final class ArgumentMcPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArgumentMcPlayer.class);

    private final @NotNull String id;
    private final @Nullable McPlayerService playerService;
    private final @NotNull PlayerResolver playerResolver;
    private final @Nullable FilterMethod filterMethod;

    private ArgumentMcPlayer(@NotNull String id, @Nullable McPlayerService playerService, @Nullable PlayerResolver playerResolver,
                             @Nullable FilterMethod filterMethod) {
        this.id = id;
        this.playerService = playerService;
        this.playerResolver = playerResolver;
        this.filterMethod = filterMethod;
    }

    public static @NotNull Argument<Supplier<LocalMcPlayer>> create(@NotNull String id, @Nullable McPlayerService playerService,
                                                                    @Nullable PlayerResolver playerResolver, @Nullable FilterMethod filterMethod) {
        var argument = new ArgumentMcPlayer(id, playerService, playerResolver, filterMethod);
        return new ArgumentWord(id).map(argument::mapInput).setSuggestionCallback(argument::suggestionCallback);
    }

    private @NotNull Supplier<LocalMcPlayer> mapInput(@Nullable String input) {
        if (this.playerService == null) return () -> null;
        if (input == null || input.isEmpty() || input.length() == 1 && input.charAt(0) == 0) {
            return () -> null;
        }

        return () -> {
            try {
                return this.playerResolver.getPlayer(input);
            } catch (StatusException exception) {
                LOGGER.error("Failed to get player data for '{}'", input, exception);
                return null;
            }
        };
    }

    private void suggestionCallback(@NotNull CommandSender sender, @NotNull CommandContext context, @NotNull Suggestion suggestion) {
        if (!(sender instanceof Player player)) return;
        if (this.playerService == null) return; // TODO: See if we can try to locally resolve instead if service is unavailable

        String input = context.getRaw(this.id);
        if (input == null || input.length() <= 2) return;

        Pageable pageable = Pageable.newBuilder().setPage(0).setSize(15).build();
        List<McPlayer> players;
        try {
            players = this.playerService.searchPlayersByUsername(player.getUuid(), input, pageable, this.filterMethod);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to search players by username '{}'", input, exception);
            return;
        }

        for (McPlayer mcPlayer : players) {
            suggestion.addEntry(new SuggestionEntry(mcPlayer.getCurrentUsername()));
        }
    }
}
