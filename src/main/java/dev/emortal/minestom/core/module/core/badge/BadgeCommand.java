package dev.emortal.minestom.core.module.core.badge;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import dev.emortal.api.grpc.badge.BadgeManagerGrpc;
import dev.emortal.api.grpc.badge.BadgeManagerProto;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.utils.command.argument.ArgumentBadge;
import io.grpc.protobuf.StatusProto;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

public class BadgeCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeCommand.class);

    private final BadgeManagerGrpc.BadgeManagerFutureStub badgeManager = GrpcStubCollection.getBadgeManagerService().orElse(null);

    public BadgeCommand() {
        super("badge");

        setCondition(Conditions::playerOnly);
        setDefaultExecutor((sender, context) -> new BadgeGui((Player) sender));

        final ArgumentLiteral setArgument = new ArgumentLiteral("set");
        final ArgumentWord badgeArgument = ArgumentBadge.create(badgeManager, "badge", true);

        addConditionalSyntax(Conditions::playerOnly, this::executeSetCurrentBadge, setArgument, badgeArgument);

        addSubcommand(new BadgeAdminSubcommand(badgeManager));
    }

    private void executeSetCurrentBadge(CommandSender sender, CommandContext context) {
        final String badgeId = context.get("badge");
        final Player player = (Player) sender;

        final var request = BadgeManagerProto.SetActivePlayerBadgeRequest.newBuilder()
                .setBadgeId(badgeId)
                .setPlayerId(player.getUuid().toString())
                .build();

        Futures.addCallback(badgeManager.setActivePlayerBadge(request), new SetCurrentBadgeCallback(sender, badgeId), ForkJoinPool.commonPool());
    }

    private record SetCurrentBadgeCallback(@NotNull CommandSender sender,
                                           @NotNull String badgeId) implements FutureCallback<BadgeManagerProto.SetActivePlayerBadgeResponse> {

        @Override
        public void onSuccess(@NotNull BadgeManagerProto.SetActivePlayerBadgeResponse result) {
            sender.sendMessage(Component.text("Set your badge to " + badgeId));
        }

        @Override
        public void onFailure(@NotNull Throwable throwable) {
            final Status status = StatusProto.fromThrowable(throwable);
            if (status == null || status.getDetailsCount() == 0) {
                LOGGER.error("Failed to set badge", throwable);
                return;
            }

            final BadgeManagerProto.SetActivePlayerBadgeErrorResponse response;
            try {
                response = status.getDetails(0).unpack(BadgeManagerProto.SetActivePlayerBadgeErrorResponse.class);
            } catch (final InvalidProtocolBufferException exception) {
                LOGGER.error("Failed to set badge", throwable);
                return;
            }

            switch (response.getReason()) {
                case PLAYER_DOESNT_HAVE_BADGE -> sender.sendMessage(Component.text("You don't have that badge"));
                default -> {
                    LOGGER.error("Failed to set badge", throwable);
                    sender.sendMessage(Component.text("Failed to set badge"));
                }
            }
        }
    }
}
