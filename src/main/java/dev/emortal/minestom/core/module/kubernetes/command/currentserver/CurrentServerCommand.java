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

    private static final String COPY_MESSAGE = """
            Proxy: %s
            Server: %s
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

        Component message = Component.text().append(
                Component.text("Proxy: ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text(currentServer.getProxyId(), NamedTextColor.LIGHT_PURPLE))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy Proxy ID", NamedTextColor.GREEN)))
                        .clickEvent(ClickEvent.copyToClipboard(currentServer.getProxyId()))
        ).append(
                Component.newline()
                        .append(Component.text("Server: ", NamedTextColor.DARK_PURPLE))
                        .append(Component.text(currentServer.getServerId(), NamedTextColor.LIGHT_PURPLE))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy Server ID", NamedTextColor.GREEN)))
                        .clickEvent(ClickEvent.copyToClipboard(currentServer.getServerId()))
        ).append(
                Component.newline()
                        .append(Component.text("Fleet: ", NamedTextColor.DARK_PURPLE))
                        .append(Component.text(currentServer.getFleetName(), NamedTextColor.LIGHT_PURPLE))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy Fleet Name", NamedTextColor.GREEN)))
                        .clickEvent(ClickEvent.copyToClipboard(currentServer.getFleetName()))
        ).append(
                Component.newline()
                        .append(Component.text("⎘ Click to copy", NamedTextColor.GREEN))
                        .clickEvent(ClickEvent.copyToClipboard(this.createCopyableData(currentServer, player)))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy all data", NamedTextColor.GREEN)))
        ).build();

        sender.sendMessage(message);
    }

    private @NotNull String createCopyableData(@NotNull CurrentServer server, @NotNull Player player) {
        return COPY_MESSAGE.formatted(
                server.getProxyId(),
                server.getServerId(),
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