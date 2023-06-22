package dev.emortal.minestom.core.module.core.badge;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import dev.emortal.api.grpc.badge.BadgeManagerGrpc;
import dev.emortal.api.grpc.badge.BadgeManagerProto;
import dev.emortal.api.grpc.mcplayer.McPlayerProto;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.utils.command.ExtraConditions;
import dev.emortal.minestom.core.utils.command.argument.ArgumentBadge;
import dev.emortal.minestom.core.utils.command.argument.ArgumentMcPlayer;
import io.grpc.protobuf.StatusProto;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public final class BadgeAdminSubcommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeAdminSubcommand.class);

    private final BadgeManagerGrpc.BadgeManagerFutureStub badgeManager;

    public BadgeAdminSubcommand(@NotNull BadgeManagerGrpc.BadgeManagerFutureStub badgeManager) {
        super("admin");
        this.badgeManager = badgeManager;

        setCondition(ExtraConditions.hasPermission("command.badge.admin"));

        final ArgumentLiteral addArgument = new ArgumentLiteral("add");
        final ArgumentLiteral removeArgument = new ArgumentLiteral("remove");
        final var playerArgument = ArgumentMcPlayer.create("player",
                GrpcStubCollection.getPlayerService().orElse(null),
                McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod.NONE
        );
        final ArgumentWord badgeArgument = ArgumentBadge.create(badgeManager, "badge", false);

        addSyntax(this::executeAddBadgeToPlayer, addArgument, playerArgument, badgeArgument);

        addSyntax(this::executeRemoveBadgeFromPlayer, removeArgument, playerArgument, badgeArgument);
    }

    private void executeAddBadgeToPlayer(CommandSender sender, CommandContext context) {
        final CompletableFuture<McPlayer> playerFuture = context.get("player");
        final String badgeId = context.get("badge");

        playerFuture.thenAccept(mcPlayer -> {
            final var request = BadgeManagerProto.AddBadgeToPlayerRequest.newBuilder()
                    .setBadgeId(badgeId)
                    .setPlayerId(mcPlayer.getId()).build();

            Futures.addCallback(badgeManager.addBadgeToPlayer(request), new AddBadgeToPlayerCallback(sender), ForkJoinPool.commonPool());
        });
    }

    private record AddBadgeToPlayerCallback(@NotNull CommandSender sender) implements FutureCallback<BadgeManagerProto.AddBadgeToPlayerResponse> {

        @Override
        public void onSuccess(@NotNull BadgeManagerProto.AddBadgeToPlayerResponse result) {
            sender.sendMessage(Component.text("Added badge to player"));
        }

        @Override
        public void onFailure(@NotNull Throwable throwable) {
            final Status status = StatusProto.fromThrowable(throwable);
            if (status == null || status.getDetailsCount() == 0) {
                LOGGER.error("Failed to add badge to player", throwable);
                return;
            }

            final BadgeManagerProto.AddBadgeToPlayerErrorResponse response;
            try {
                response = status.getDetails(0).unpack(BadgeManagerProto.AddBadgeToPlayerErrorResponse.class);
            } catch (final InvalidProtocolBufferException exception) {
                LOGGER.error("Failed to add badge to player", exception);
                return;
            }

            switch (response.getReason()) {
                case PLAYER_ALREADY_HAS_BADGE -> sender.sendMessage(Component.text("Player already has that badge"));
                default -> {
                    LOGGER.error("Failed to add badge to player", throwable);
                    sender.sendMessage(Component.text("Failed to add badge to player"));
                }
            }
        }
    }

    private void executeRemoveBadgeFromPlayer(CommandSender sender, CommandContext context) {
        final CompletableFuture<McPlayer> playerFuture = context.get("player");
        final String badgeId = context.get("badge");

        playerFuture.thenAccept(mcPlayer -> {
            final var request = BadgeManagerProto.RemoveBadgeFromPlayerRequest.newBuilder()
                    .setBadgeId(badgeId)
                    .setPlayerId(mcPlayer.getId()).build();

            Futures.addCallback(badgeManager.removeBadgeFromPlayer(request), new RemoveBadgeFromPlayerCallback(sender), ForkJoinPool.commonPool());
        });
    }

    private record RemoveBadgeFromPlayerCallback(@NotNull CommandSender sender) implements FutureCallback<BadgeManagerProto.RemoveBadgeFromPlayerResponse> {

        @Override
        public void onSuccess(@NotNull BadgeManagerProto.RemoveBadgeFromPlayerResponse result) {
            sender.sendMessage(Component.text("Removed badge from player"));
        }

        @Override
        public void onFailure(@NotNull Throwable throwable) {
            Status status = StatusProto.fromThrowable(throwable);
            if (status == null || status.getDetailsCount() == 0) {
                LOGGER.error("Failed to remove badge from player", throwable);
                return;
            }

            final BadgeManagerProto.RemoveBadgeFromPlayerErrorResponse response;
            try {
                response = status.getDetails(0).unpack(BadgeManagerProto.RemoveBadgeFromPlayerErrorResponse.class);
            } catch (final InvalidProtocolBufferException exception) {
                LOGGER.error("Failed to remove badge from player", exception);
                return;
            }

            switch (response.getReason()) {
                case PLAYER_DOESNT_HAVE_BADGE -> sender.sendMessage(Component.text("Player doesn't have that badge"));
                default -> {
                    LOGGER.error("Failed to remove badge from player", throwable);
                    sender.sendMessage(Component.text("Failed to remove badge from player"));
                }
            }
        }
    }
}
