package dev.emortal.minestom.core.module.matchmaker.session;

import dev.emortal.api.grpc.matchmaker.MatchmakerProto.GetPlayerQueueInfoResponse;
import dev.emortal.api.liveconfigparser.configs.ConfigProvider;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.message.matchmaker.MatchCreatedMessage;
import dev.emortal.api.message.matchmaker.PendingMatchCreatedMessage;
import dev.emortal.api.message.matchmaker.PendingMatchDeletedMessage;
import dev.emortal.api.message.matchmaker.PendingMatchUpdatedMessage;
import dev.emortal.api.message.matchmaker.TicketCreatedMessage;
import dev.emortal.api.message.matchmaker.TicketDeletedMessage;
import dev.emortal.api.message.matchmaker.TicketUpdatedMessage;
import dev.emortal.api.model.matchmaker.PendingMatch;
import dev.emortal.api.model.matchmaker.Ticket;
import dev.emortal.api.service.matchmaker.MatchmakerService;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class MatchmakingSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakingSessionManager.class);

    private static final String QUEUE_RESTORED_MESSAGE = "<green>Your queue for <mode> has been transferred!</green>";
    private static final String QUEUE_RESTORE_FAILED_MESSAGE = "<red>Your queue for <mode> could not be transferred! Please tell a staff member.</red>";

    private final @NotNull MatchmakerService matchmaker;
    private final @NotNull MatchmakingSession.Creator sessionCreator;
    private final @NotNull ConfigProvider<GameModeConfig> gameModes;

    private final Map<UUID, MatchmakingSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketCache = new ConcurrentHashMap<>();

    // TODO: note that tickets technically memory leak but it's so small and cleaned up when the ticket is deleted.
    public MatchmakingSessionManager(@NotNull EventNode<Event> eventNode, @NotNull MatchmakerService matchmaker, @NotNull MessagingModule messaging,
                                     @NotNull ConfigProvider<GameModeConfig> gameModes, @NotNull MatchmakingSession.Creator sessionCreator) {
        this.matchmaker = matchmaker;
        this.sessionCreator = sessionCreator;
        this.gameModes = gameModes;

        eventNode.addListener(AsyncPlayerConfigurationEvent.class, this::handlePlayerLogin);

        eventNode.addListener(PlayerDisconnectEvent.class, event -> {
            UUID playerId = event.getPlayer().getUuid();

            MatchmakingSession session = this.sessions.remove(playerId);
            if (session == null) return;

            this.destroySession(session);
        });

        messaging.addListener(TicketCreatedMessage.class, message -> this.onTicketCreate(message.getTicket()));

        messaging.addListener(TicketDeletedMessage.class, message -> this.onTicketDelete(message.getTicket(), message.getReason()));

        // NOTE: This logic requires on this method being run synchronously.
        // NOTE 2: This logic is only for players leaving/joining a ticket. It doesn't need anything else.
        messaging.addListener(TicketUpdatedMessage.class, message -> this.onTicketUpdated(message.getNewTicket()));

        messaging.addListener(PendingMatchCreatedMessage.class,
                message -> this.onPendingMatchChange(message.getPendingMatch(), MatchmakingSession::onPendingMatchCreate));

        messaging.addListener(PendingMatchUpdatedMessage.class,
                message -> this.onPendingMatchChange(message.getPendingMatch(), MatchmakingSession::onPendingMatchUpdate));

        messaging.addListener(PendingMatchDeletedMessage.class, message -> {
            // Ignore this message as it's handled by the MatchCreatedMessage (and the proxy will message them :D)
            if (message.getReason() == PendingMatchDeletedMessage.Reason.MATCH_CREATED) return;

            this.onPendingMatchChange(message.getPendingMatch(), MatchmakingSession::onPendingMatchCancelled);
        });

        messaging.addListener(MatchCreatedMessage.class, message -> {
            for (Ticket ticket : message.getMatch().getTicketsList()) {
                for (String playerId : ticket.getPlayerIdsList()) {
                    this.deleteSession(playerId, MatchmakingSession.DeleteReason.MATCH_CREATED, false);
                }
            }
        });
    }

    private void onTicketCreate(@NotNull Ticket ticket) {
        boolean shouldCache = false;
        for (String playerId : ticket.getPlayerIdsList()) {
            UUID uuid = UUID.fromString(playerId);

            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
            if (player == null) continue;

            GameModeConfig gameMode = this.gameModes.getConfig(ticket.getGameModeId());
            MatchmakingSession session = this.sessionCreator.create(player, gameMode, ticket);
            this.sessions.put(uuid, session);
            shouldCache = true;
        }

        if (shouldCache) {
            this.ticketCache.put(ticket.getId(), ticket);
        }
    }

    private void onTicketDelete(@NotNull Ticket ticket, @NotNull TicketDeletedMessage.Reason messageReason) {
        Ticket cachedTicket = this.ticketCache.remove(ticket.getId());
        if (cachedTicket == null) return; // If the ticket is not cached, there should be no sessions with it.

        MatchmakingSession.DeleteReason deleteReason = switch (messageReason) {
            case MATCH_CREATED -> MatchmakingSession.DeleteReason.MATCH_CREATED;
            case MANUAL_DEQUEUE -> MatchmakingSession.DeleteReason.MANUAL_DEQUEUE;
            case GAME_MODE_DELETED -> MatchmakingSession.DeleteReason.GAME_MODE_DELETED;
            default -> MatchmakingSession.DeleteReason.UNKNOWN;
        };

        for (String playerId : ticket.getPlayerIdsList()) {
            this.deleteSession(playerId, deleteReason, true);
        }
    }

    private void onTicketUpdated(@NotNull Ticket newTicket) {
        Ticket oldTicket = this.ticketCache.get(newTicket.getId());
        GameModeConfig gameMode = this.gameModes.getConfig(newTicket.getGameModeId());

        // Perform adding operations and update existing MatchmakingSessions
        for (String playerId : newTicket.getPlayerIdsList()) {
            MatchmakingSession session = this.sessions.get(UUID.fromString(playerId));
            if (session != null) {
                session.setTicket(newTicket);
                continue;
            }

            UUID uuid = UUID.fromString(playerId);
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
            if (player == null) continue;

            session = this.sessionCreator.create(player, gameMode, newTicket);
            this.sessions.put(uuid, session);
        }

        // Calculate removed if oldTicket is not null
        if (oldTicket != null) {
            for (String playerId : oldTicket.getPlayerIdsList()) {
                if (newTicket.getPlayerIdsList().contains(playerId)) continue;
                this.deleteSession(playerId, MatchmakingSession.DeleteReason.UNKNOWN, true);
            }
        }

        this.ticketCache.put(newTicket.getId(), newTicket);
    }

    private void onPendingMatchChange(@NotNull PendingMatch match, @NotNull BiConsumer<MatchmakingSession, PendingMatch> action) {
        for (String ticketId : match.getTicketIdsList()) {
            Ticket ticket = this.ticketCache.get(ticketId);
            if (ticket == null) continue;

            for (String playerId : ticket.getPlayerIdsList()) {
                UUID uuid = UUID.fromString(playerId);

                MatchmakingSession session = this.sessions.get(uuid);
                if (session == null) continue;

                action.accept(session, match);
            }
        }
    }

    private void deleteSession(@NotNull String stringId, @NotNull MatchmakingSession.DeleteReason reason, boolean playerMustBeOnline) {
        UUID id = UUID.fromString(stringId);

        if (playerMustBeOnline) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id);
            if (player == null) return;
        }

        MatchmakingSession session = this.sessions.remove(id);
        if (session == null) return;

        session.notifyDeletion(reason);
        this.destroySession(session);
    }

    private void destroySession(@NotNull MatchmakingSession session) {
        this.sessions.remove(session.getPlayer().getUuid());
        session.destroy();
    }

    private void handlePlayerLogin(@NotNull AsyncPlayerConfigurationEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUuid();

        GetPlayerQueueInfoResponse queueInfo;
        try {
            queueInfo = this.matchmaker.getPlayerQueueInfo(playerId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to get queue info for '{}'", player.getUsername(), exception);
            player.sendMessage(Component.text("An unknown error occurred", NamedTextColor.RED));
            return;
        }

        // Queue info is not required. A player might not be in a queue.
        if (queueInfo == null) return;

        Ticket ticket = queueInfo.getTicket();
        GameModeConfig mode = this.gameModes.getConfig(ticket.getGameModeId());

        var modeName = Placeholder.unparsed("mode", mode == null ? ticket.getGameModeId() : mode.friendlyName());
        if (mode == null) {
            LOGGER.error("Failed to get game mode config '{}'", ticket.getGameModeId());
            player.sendMessage(MiniMessage.miniMessage().deserialize(QUEUE_RESTORE_FAILED_MESSAGE, modeName));
            return;
        }

        MatchmakingSession session = this.sessionCreator.create(player, mode, ticket);
        this.sessions.put(playerId, session);
        this.ticketCache.put(ticket.getId(), ticket);

        player.sendMessage(MiniMessage.miniMessage().deserialize(QUEUE_RESTORED_MESSAGE, modeName));
    }
}
