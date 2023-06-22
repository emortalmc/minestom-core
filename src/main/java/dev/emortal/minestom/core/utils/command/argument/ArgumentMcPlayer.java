package dev.emortal.minestom.core.utils.command.argument;

import dev.emortal.api.grpc.mcplayer.McPlayerGrpc;
import dev.emortal.api.grpc.mcplayer.McPlayerProto;
import dev.emortal.api.model.common.Pageable;
import dev.emortal.api.model.mcplayer.McPlayer;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

public class ArgumentMcPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArgumentMcPlayer.class);

    private final @NotNull String id;
    private final McPlayerGrpc.McPlayerFutureStub mcPlayerService;
    private final @Nullable McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod filterMethod;

    private ArgumentMcPlayer(@NotNull String id, McPlayerGrpc.McPlayerFutureStub mcPlayerService,
                             @Nullable McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod filterMethod) {
        this.id = id;
        this.mcPlayerService = mcPlayerService;
        this.filterMethod = filterMethod;
    }

    public static Argument<CompletableFuture<McPlayer>> create(@NotNull String id,
                                                               @NotNull McPlayerGrpc.McPlayerFutureStub mcPlayerService,
                                                               @Nullable McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod filterMethod) {

        final ArgumentMcPlayer argument = new ArgumentMcPlayer(id, mcPlayerService, filterMethod);

        return new ArgumentWord(id)
                .map(argument::mapInput)
                .setSuggestionCallback(argument::suggestionCallback);
    }

    private CompletableFuture<McPlayer> mapInput(@Nullable String input) {
        if (input == null || input.isEmpty() || input.length() == 1 && input.charAt(0) == 0) {
            return CompletableFuture.completedFuture(null);
        }

        final var playerReqFuture = mcPlayerService.getPlayerByUsername(McPlayerProto.PlayerUsernameRequest.newBuilder().setUsername(input).build());

        return CompletableFuture.supplyAsync(() -> {
            try {
                return playerReqFuture.get().getPlayer();
            } catch (final Exception exception) {
                throw new CompletionException(exception);
            }
        }, ForkJoinPool.commonPool()).exceptionally(exception -> {
            LOGGER.error("Failed to retrieve McPlayer (input: {})", input, exception);
            return null;
        });
    }

    private void suggestionCallback(CommandSender sender, CommandContext context, Suggestion suggestion) {
        if (!(sender instanceof Player player)) return;

        final String input = context.getRaw(this.id);
        if (input == null || input.length() <= 2) return;

        final var reqBuilder = McPlayerProto.SearchPlayersByUsernameRequest.newBuilder()
                .setIssuerId(player.getUuid().toString())
                .setSearchUsername(input)
                .setPageable(Pageable.newBuilder().setPage(0).setSize(15));

        if (this.filterMethod != null) {
            reqBuilder.setFilterMethod(this.filterMethod);
        }

        final var searchReqFuture = this.mcPlayerService.searchPlayersByUsername(reqBuilder.build());

        try {
            final var response = searchReqFuture.get();
            for (final McPlayer lPlayer : response.getPlayersList()) {
                suggestion.addEntry(new SuggestionEntry(lPlayer.getCurrentUsername()));
            }
        } catch (final ExecutionException | InterruptedException exception) {
            LOGGER.error("Failed to get player suggestions (input: {})", input, exception);
        }
    }
}
