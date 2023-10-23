package dev.emortal.minestom.core.module.kubernetes.command.currentserver;

import dev.emortal.api.model.mcplayer.CurrentServer;
import dev.emortal.api.service.playertracker.PlayerTrackerService;
import io.grpc.StatusRuntimeException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CurrentServerCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentServerCommand.class);

    private static final String MESSAGE = """
            <dark_purple>Proxy: <light_purple><proxy_id>
            <dark_purple>Server: <light_purple><server_id>
            <dark_purple>Fleet: <light_purple><fleet_id>""";
    private static final String COPY_MESSAGE = """
            Proxy: %s
            Server: %s
            Instance: %s
            Position: %s""";

    private final @NotNull PlayerTrackerService playerTracker;

    public CurrentServerCommand(@NotNull PlayerTrackerService playerTracker) {
        super("whereami");
        this.playerTracker = playerTracker;

        this.setCondition(Conditions::playerOnly);
        this.setDefaultExecutor(this::onExecute);
    }

    private void onExecute(@NotNull CommandSender sender, @NotNull CommandContext context) {
        Player player = (Player) sender;

        CurrentServer currentServer;
        try {
            currentServer = this.playerTracker.getServer(player.getUuid());
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to get current server for '{}'", player.getUsername());
            player.sendMessage(Component.text("An unknown error occurred", NamedTextColor.RED));
            return;
        }

        if (currentServer == null) {
            player.sendMessage(Component.text("An unknown error occurred", NamedTextColor.RED));
            return;
        }

        var serverId = Placeholder.unparsed("server_id", currentServer.getServerId());
        var proxyId = Placeholder.unparsed("proxy_id", currentServer.getProxyId());
        var fleetId = Placeholder.unparsed("fleet_id", currentServer.getFleetName());

        var message = MiniMessage.miniMessage().deserialize(MESSAGE, serverId, proxyId, fleetId)
                .clickEvent(ClickEvent.copyToClipboard(this.createCopyableData(currentServer, player)))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GREEN)));
        sender.sendMessage(message);
    }

    private @NotNull String createCopyableData(@NotNull CurrentServer server, @NotNull Player player) {
        return COPY_MESSAGE.formatted(
                server.getProxyId(),
                server.getServerId(),
                player.getInstance().getUniqueId(),
                this.formatPos(player.getPosition())
        );
    }

    private @NotNull String formatPos(@NotNull Pos pos) {
        return String.format(
                "x: %.2f, y: %.2f, z: %.2f, yaw: %.2f, pitch: %.2f",
                pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch()
        );
    }
}