package dev.emortal.minestom.core.utils.command.argument;

import dev.emortal.api.grpc.mcplayer.McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod;
import dev.emortal.api.model.common.Pageable;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.service.mcplayer.McPlayerService;
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

    public static @NotNull Argument<Supplier<McPlayer>> create(@NotNull String id, @NotNull McPlayerService mcPlayerService,
                                                               @Nullable FilterMethod filterMethod) {
        var argument = new ArgumentMcPlayer(id, mcPlayerService, filterMethod);
        return new ArgumentWord(id).map(argument::mapInput).setSuggestionCallback(argument::suggestionCallback);
    }

    private final @NotNull String id;
    private final @NotNull McPlayerService mcPlayerService;
    private final @Nullable FilterMethod filterMethod;

    private ArgumentMcPlayer(@NotNull String id, @NotNull McPlayerService mcPlayerService, @Nullable FilterMethod filterMethod) {
        this.id = id;
        this.mcPlayerService = mcPlayerService;
        this.filterMethod = filterMethod;
    }

    private @NotNull Supplier<McPlayer> mapInput(@Nullable String input) {
        if (input == null || input.isEmpty() || input.length() == 1 && input.charAt(0) == 0) {
            return () -> null;
        }

        return () -> {
            try {
                return this.mcPlayerService.getPlayerByUsername(input);
            } catch (Exception exception) {
                LOGGER.error("Failed to retrieve McPlayer (input: {})", input, exception);
                return null;
            }
        };
    }

    private void suggestionCallback(@NotNull CommandSender sender, @NotNull CommandContext context, @NotNull Suggestion suggestion) {
        if (!(sender instanceof Player player)) return;

        String input = context.getRaw(this.id);
        if (input == null || input.length() <= 2) return;

        Pageable pageable = Pageable.newBuilder().setPage(0).setSize(15).build();
        List<McPlayer> results = this.mcPlayerService.searchPlayersByUsername(player.getUuid(), input, pageable, this.filterMethod);

        for (McPlayer result : results) {
            suggestion.addEntry(new SuggestionEntry(result.getCurrentUsername()));
        }
    }
}
