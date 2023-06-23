package dev.emortal.minestom.core.module.kubernetes.command.currentserver;

import com.google.common.util.concurrent.Futures;
import dev.emortal.api.grpc.playertracker.PlayerTrackerGrpc;
import dev.emortal.api.grpc.playertracker.PlayerTrackerProto;
import dev.emortal.api.model.playertracker.PlayerLocation;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import java.util.concurrent.ForkJoinPool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CurrentServerCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentServerCommand.class);

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final String MESSAGE = """
            <dark_purple>Proxy: <light_purple><proxy_id>
            <dark_purple>Server: <light_purple><server_id>""";
    private static final String COPY_MESSAGE = """
            Proxy: %s
            Server: %s
            Instance: %s
            Position: %s""";

    private final PlayerTrackerGrpc.PlayerTrackerFutureStub playerTracker = GrpcStubCollection.getPlayerTrackerService().orElse(null);

    public CurrentServerCommand() {
        super("whereami");

        setCondition(Conditions::playerOnly);
        setDefaultExecutor(this::onExecute);
    }

    private void onExecute(CommandSender sender, CommandContext context) {
        final Player player = (Player) sender;

        if (playerTracker == null) {
            sender.sendMessage(Component.text("Player tracker service is not available, cannot get your current server. Please try again later.", NamedTextColor.RED));
            return;
        }

        final var request = PlayerTrackerProto.GetPlayerServerRequest.newBuilder().setPlayerId(player.getUuid().toString()).build();
        Futures.addCallback(playerTracker.getPlayerServer(request), FunctionalFutureCallback.create(
                response -> {
                    final PlayerLocation location = response.getServer();

                    final var serverId = Placeholder.unparsed("server_id", location.getServerId());
                    final var proxyId = Placeholder.unparsed("proxy_id", location.getProxyId());

                    sender.sendMessage(MINI_MESSAGE.deserialize(MESSAGE, serverId, proxyId)
                            .clickEvent(ClickEvent.copyToClipboard(createCopyableData(location, player)))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GREEN))));
                },
                exception -> LOGGER.error("Failed to retrieve player server", exception)
        ), ForkJoinPool.commonPool());
    }

    private String createCopyableData(@NotNull PlayerLocation location, @NotNull Player player) {
        return COPY_MESSAGE.formatted(
                location.getProxyId(),
                location.getServerId(),
                player.getInstance().getUniqueId(),
                formatPos(player.getPosition())
        );
    }

    private String formatPos(@NotNull Pos pos) {
        return String.format(
                "x: %s, y: %s, z: %s, yaw: %s, pitch: %s",
                DECIMAL_FORMAT.format(pos.x()), DECIMAL_FORMAT.format(pos.y()),
                DECIMAL_FORMAT.format(pos.z()), DECIMAL_FORMAT.format(pos.yaw()),
                DECIMAL_FORMAT.format(pos.pitch())
        );
    }
}