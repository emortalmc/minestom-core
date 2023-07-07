package dev.emortal.minestom.core.module.matchmaker.session;

import com.google.common.util.concurrent.Futures;
import com.google.rpc.Code;
import com.google.rpc.Status;
import dev.emortal.api.kurushimi.GetPlayerQueueInfoRequest;
import dev.emortal.api.kurushimi.MatchmakerGrpc;
import dev.emortal.api.kurushimi.PendingMatch;
import dev.emortal.api.kurushimi.Ticket;
import dev.emortal.api.kurushimi.messages.MatchCreatedMessage;
import dev.emortal.api.kurushimi.messages.PendingMatchCreatedMessage;
import dev.emortal.api.kurushimi.messages.PendingMatchDeletedMessage;
import dev.emortal.api.kurushimi.messages.PendingMatchUpdatedMessage;
import dev.emortal.api.kurushimi.messages.TicketCreatedMessage;
import dev.emortal.api.kurushimi.messages.TicketDeletedMessage;
import dev.emortal.api.kurushimi.messages.TicketUpdatedMessage;
import dev.emortal.api.liveconfigparser.configs.ConfigUpdate;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.utils.callback.FunctionalFutureCallback;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import io.grpc.protobuf.StatusProto;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

public final class MatchmakingSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakingSessionManager.class);

    private static final String QUEUE_RESTORED_MESSAGE = "<green>Your queue for <mode> has been transferred!</green>";
    private static final String QUEUE_RESTORE_FAILED_MESSAGE = "<red>Your queue for <mode> could not be transferred! Please tell a staff member.</red>";

    private final MatchmakerGrpc.MatchmakerFutureStub matchmaker;
    private final TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator;

    private final Map<UUID, MatchmakingSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketCache = new ConcurrentHashMap<>();

    private final GameModeCollection gameModeCollection;

    // TODO: note that tickets technically memory leak but it's so small and cleaned up when the ticket is deleted.
    public MatchmakingSessionManager(@NotNull EventNode<Event> eventNode, @NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker,
                                     @NotNull MessagingModule messaging, @NotNull GameModeCollection gameModeCollection,
                                     @NotNull TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator) {
        this.matchmaker = matchmaker;
        this.sessionCreator = sessionCreator;
        this.gameModeCollection = gameModeCollection;

        eventNode.addListener(PlayerLoginEvent.class, this::handlePlayerLogin);

        eventNode.addListener(PlayerDisconnectEvent.class, event -> {
            UUID playerId = event.getPlayer().getUuid();
            MatchmakingSession session = this.sessions.remove(playerId);
            if (session == null) return;

            destroySession(session);
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
                    deleteSession(playerId, MatchmakingSession.DeleteReason.MATCH_CREATED, false);
                }
            }
        });
    }

    private void onTicketCreate(Ticket ticket) {
        boolean shouldCache = false;
        for (String playerId : ticket.getPlayerIdsList()) {
            UUID uuid = UUID.fromString(playerId);
            Player player = MinecraftServer.getConnectionManager().getPlayer(uuid);
            if (player == null) continue;

            GameModeConfig gameMode = this.gameModeCollection.getConfig(ticket.getGameModeId());
            MatchmakingSession session = sessionCreator.apply(player, gameMode, ticket);
            this.sessions.put(uuid, session);
            shouldCache = true;
        }

        if (shouldCache) {
            this.ticketCache.put(ticket.getId(), ticket);
        }
    }

    private void onTicketDelete(Ticket ticket, TicketDeletedMessage.Reason messageReason) {
        Ticket cachedTicket = this.ticketCache.remove(ticket.getId());
        if (cachedTicket == null) return; // If the ticket is not cached, there should be no sessions with it.

        var deleteReason = switch (messageReason) {
            case MATCH_CREATED -> MatchmakingSession.DeleteReason.MATCH_CREATED;
            case MANUAL_DEQUEUE -> MatchmakingSession.DeleteReason.MANUAL_DEQUEUE;
            case GAME_MODE_DELETED -> MatchmakingSession.DeleteReason.GAME_MODE_DELETED;
            default -> MatchmakingSession.DeleteReason.UNKNOWN;
        };

        for (String playerId : ticket.getPlayerIdsList()) {
            deleteSession(playerId, deleteReason, true);
        }
    }

    private void onTicketUpdated(Ticket newTicket) {
        Ticket oldTicket = this.ticketCache.get(newTicket.getId());
        GameModeConfig gameMode = this.gameModeCollection.getConfig(newTicket.getGameModeId());

        // Perform adding operations and update existing MatchmakingSessions
        for (String playerId : newTicket.getPlayerIdsList()) {
            MatchmakingSession session = this.sessions.get(UUID.fromString(playerId));
            if (session != null) {
                session.setTicket(newTicket);
                continue;
            }

            UUID uuid = UUID.fromString(playerId);
            Player player = MinecraftServer.getConnectionManager().getPlayer(uuid);
            if (player == null) continue;

            session = sessionCreator.apply(player, gameMode, newTicket);
            this.sessions.put(uuid, session);
        }

        // Calculate removed if oldTicket is not null
        if (oldTicket != null) {
            for (String playerId : oldTicket.getPlayerIdsList()) {
                if (newTicket.getPlayerIdsList().contains(playerId)) continue;
                deleteSession(playerId, MatchmakingSession.DeleteReason.UNKNOWN, true);
            }
        }

        this.ticketCache.put(newTicket.getId(), newTicket);
    }

    private void onPendingMatchChange(PendingMatch match, BiConsumer<MatchmakingSession, PendingMatch> action) {
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

    private void deleteSession(String stringId, MatchmakingSession.DeleteReason reason, boolean playerMustBeOnline) {
        UUID id = UUID.fromString(stringId);

        if (playerMustBeOnline) {
            Player player = MinecraftServer.getConnectionManager().getPlayer(id);
            if (player == null) return;
        }

        MatchmakingSession session = this.sessions.remove(id);
        if (session == null) return;

        session.notifyDeletion(reason);
        destroySession(session);
    }

    private void destroySession(@NotNull MatchmakingSession session) {
        this.sessions.remove(session.getPlayer().getUuid());
        session.destroy();
    }

    private void handlePlayerLogin(@NotNull PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUuid();
        var infoRequest = GetPlayerQueueInfoRequest.newBuilder().setPlayerId(playerId.toString()).build();

        Futures.addCallback(this.matchmaker.getPlayerQueueInfo(infoRequest), FunctionalFutureCallback.create(
                response -> {
                    Ticket ticket = response.getTicket();
                    GameModeConfig mode = this.gameModeCollection.getConfig(ticket.getGameModeId());

                    var modeName = Placeholder.unparsed("mode", mode == null ? ticket.getGameModeId() : mode.friendlyName());
                    if (mode == null) {
                        LOGGER.error("Failed to get game mode config for player " + playerId + " with game mode ID " + ticket.getGameModeId());
                        player.sendMessage(MiniMessage.miniMessage().deserialize(QUEUE_RESTORE_FAILED_MESSAGE, modeName));
                        return;
                    }

                    MatchmakingSession session = this.sessionCreator.apply(player, mode, ticket);
                    this.sessions.put(playerId, session);
                    this.ticketCache.put(ticket.getId(), ticket);

                    player.sendMessage(MiniMessage.miniMessage().deserialize(QUEUE_RESTORED_MESSAGE, modeName));
                },
                throwable -> {
                    Status status = StatusProto.fromThrowable(throwable);
                    if (status.getCode() == Code.NOT_FOUND_VALUE) return; // Player is not in a queue

                    LOGGER.error("Failed to get player queue info for player " + playerId, throwable);
                }
        ), ForkJoinPool.commonPool());
    }
}
