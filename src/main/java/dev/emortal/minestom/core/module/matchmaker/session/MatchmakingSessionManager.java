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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.network.ConnectionManager;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

public class MatchmakingSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakingSessionManager.class);

    private static final String QUEUE_RESTORED_MESSAGE = "<green>Your queue for <mode> has been transferred!</green>";
    private static final String QUEUE_RESTORE_FAILED_MESSAGE = "<red>Your queue for <mode> could not be transferred! Please tell a staff member.</red>";

    private final @NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker;
    private final @NotNull TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator;

    private final Map<UUID, MatchmakingSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketCache = new ConcurrentHashMap<>();

    private final @NotNull Map<String, GameModeConfig> configs = new ConcurrentHashMap<>();

    // TODO: note that tickets technically memory leak but it's so small and cleaned up when the ticket is deleted.
    public MatchmakingSessionManager(@NotNull EventNode<Event> eventNode, @NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker,
                                     @NotNull MessagingModule messaging, @NotNull GameModeCollection gameModeCollection,
                                     @NotNull TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator) {
        this.matchmaker = matchmaker;
        this.sessionCreator = sessionCreator;

        // Get game modes
        gameModeCollection.getAllConfigs(this::handleConfigUpdate).forEach(config -> this.configs.put(config.getId(), config));

        ConnectionManager connectionManager = MinecraftServer.getConnectionManager();

        eventNode.addListener(PlayerLoginEvent.class, this::handlePlayerLogin);

        eventNode.addListener(PlayerDisconnectEvent.class, event -> {
            UUID playerId = event.getPlayer().getUuid();
            MatchmakingSession session = this.sessions.remove(playerId);
            if (session == null) return;

            this.destroySession(session);
        });

        messaging.addListener(TicketCreatedMessage.class, message -> {
            Ticket ticket = message.getTicket();

            boolean shouldCache = false;

            for (String playerId : ticket.getPlayerIdsList()) {
                UUID uuid = UUID.fromString(playerId);
                Player player = connectionManager.getPlayer(uuid);
                if (player == null) continue;

                GameModeConfig gameMode = this.configs.get(ticket.getGameModeId());

                MatchmakingSession session = this.sessionCreator.apply(player, gameMode, ticket);
                this.sessions.put(uuid, session);
                shouldCache = true;
            }

            if (shouldCache) {
                this.ticketCache.put(ticket.getId(), ticket);
            }
        });

        messaging.addListener(TicketDeletedMessage.class, message -> {
            Ticket ticket = message.getTicket();

            Ticket removedTicket = this.ticketCache.remove(ticket.getId());
            if (removedTicket == null) return; // If the ticket is not cached, there should be no sessions with it.

            for (String playerId : ticket.getPlayerIdsList()) {
                UUID uuid = UUID.fromString(playerId);
                Player player = connectionManager.getPlayer(uuid);
                if (player == null) continue;

                MatchmakingSession session = this.sessions.remove(uuid);
                if (session == null) continue;

                MatchmakingSession.DeleteReason deleteReason = switch (message.getReason()) {
                    case MATCH_CREATED -> MatchmakingSession.DeleteReason.MATCH_CREATED;
                    case MANUAL_DEQUEUE -> MatchmakingSession.DeleteReason.MANUAL_DEQUEUE;
                    case GAME_MODE_DELETED -> MatchmakingSession.DeleteReason.GAME_MODE_DELETED;
                    default -> MatchmakingSession.DeleteReason.UNKNOWN;
                };

                session.notifyDeletion(deleteReason);
                this.destroySession(session);
            }
        });

        // NOTE: This logic requires on this method being run synchronously.
        // NOTE 2: This logic is only for players leaving/joining a ticket. It doesn't need anything else.
        messaging.addListener(TicketUpdatedMessage.class, message -> {
            Ticket newTicket = message.getNewTicket();
            Ticket oldTicket = this.ticketCache.get(newTicket.getId());

            GameModeConfig gameMode = this.configs.get(newTicket.getGameModeId());

            // Perform adding operations and update existing MatchmakingSessions
            for (String playerId : newTicket.getPlayerIdsList()) {
                MatchmakingSession session = this.sessions.get(UUID.fromString(playerId));
                if (session != null) {
                    session.setTicket(newTicket);
                    continue;
                }

                UUID uuid = UUID.fromString(playerId);
                Player player = connectionManager.getPlayer(uuid);
                if (player == null) continue;

                session = this.sessionCreator.apply(player, gameMode, newTicket);
                this.sessions.put(uuid, session);
            }

            // Calculate removed if oldTicket is not null
            if (oldTicket != null) {
                for (String playerId : oldTicket.getPlayerIdsList()) {
                    if (newTicket.getPlayerIdsList().contains(playerId)) continue;

                    UUID uuid = UUID.fromString(playerId);
                    Player player = connectionManager.getPlayer(uuid);
                    if (player == null) continue;

                    MatchmakingSession session = this.sessions.remove(uuid);
                    if (session == null) continue;

                    session.notifyDeletion(MatchmakingSession.DeleteReason.UNKNOWN);
                    this.destroySession(session);
                }
            }

            this.ticketCache.put(newTicket.getId(), newTicket);
        });

        messaging.addListener(PendingMatchCreatedMessage.class, message -> {
            PendingMatch pendingMatch = message.getPendingMatch();
            for (String ticketId : pendingMatch.getTicketIdsList()) {
                Ticket ticket = this.ticketCache.get(ticketId);
                if (ticket == null) continue;

                for (String playerId : ticket.getPlayerIdsList()) {
                    UUID uuid = UUID.fromString(playerId);

                    MatchmakingSession session = this.sessions.get(uuid);
                    if (session == null) continue;

                    session.onPendingMatchCreate(pendingMatch);
                }
            }
        });

        messaging.addListener(PendingMatchUpdatedMessage.class, message -> {
            PendingMatch pendingMatch = message.getPendingMatch();
            for (String ticketId : pendingMatch.getTicketIdsList()) {
                Ticket ticket = this.ticketCache.get(ticketId);
                if (ticket == null) continue;

                for (String playerId : ticket.getPlayerIdsList()) {
                    UUID uuid = UUID.fromString(playerId);

                    MatchmakingSession session = this.sessions.get(uuid);
                    if (session == null) continue;

                    session.onPendingMatchUpdate(pendingMatch);
                }
            }
        });

        messaging.addListener(PendingMatchDeletedMessage.class, message -> {
            // Ignore this message as it's handled by the MatchCreatedMessage (and the proxy will message them :D)
            if (message.getReason() == PendingMatchDeletedMessage.Reason.MATCH_CREATED) return;

            PendingMatch pendingMatch = message.getPendingMatch();
            for (String ticketId : pendingMatch.getTicketIdsList()) {
                Ticket ticket = this.ticketCache.get(ticketId);
                if (ticket == null) continue;

                for (String playerId : ticket.getPlayerIdsList()) {
                    UUID uuid = UUID.fromString(playerId);

                    MatchmakingSession session = this.sessions.get(uuid);
                    if (session == null) continue;

                    session.onPendingMatchCancelled(pendingMatch);
                }
            }
        });

        messaging.addListener(MatchCreatedMessage.class, message -> {
            for (Ticket ticket : message.getMatch().getTicketsList()) {
                for (String playerId : ticket.getPlayerIdsList()) {
                    UUID uuid = UUID.fromString(playerId);

                    MatchmakingSession session = this.sessions.get(uuid);
                    if (session == null) continue;

                    session.notifyDeletion(MatchmakingSession.DeleteReason.MATCH_CREATED);
                    this.destroySession(session);
                }
            }
        });
    }

    private void destroySession(@NotNull MatchmakingSession session) {
        this.sessions.remove(session.getPlayer().getUuid());
        session.destroy();
    }

    private void handleConfigUpdate(@NotNull ConfigUpdate<GameModeConfig> update) {
        GameModeConfig config = update.getConfig();

        switch (update.getType()) {
            case CREATE -> this.configs.put(config.getId(), config);
            case DELETE -> this.configs.remove(config.getId());
            case MODIFY -> {
                this.configs.put(config.getId(), config);
            }
        }
    }

    private void handlePlayerLogin(@NotNull PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUuid();
        var infoReqFuture = this.matchmaker.getPlayerQueueInfo(GetPlayerQueueInfoRequest.newBuilder().setPlayerId(playerId.toString()).build());

        Futures.addCallback(infoReqFuture, FunctionalFutureCallback.create(
                response -> {
                    Ticket ticket = response.getTicket();
                    GameModeConfig mode = this.configs.get(ticket.getGameModeId());
                    if (mode == null) {
                        LOGGER.error("Failed to get game mode config for player " + playerId + " with game mode ID " + ticket.getGameModeId());
                        player.sendMessage(MiniMessage.miniMessage().deserialize(QUEUE_RESTORE_FAILED_MESSAGE, Placeholder.unparsed("mode", ticket.getGameModeId())));
                        return;
                    }

                    MatchmakingSession session = this.sessionCreator.apply(player, mode, ticket);
                    this.sessions.put(playerId, session);
                    this.ticketCache.put(ticket.getId(), ticket);

                    player.sendMessage(MiniMessage.miniMessage().deserialize(QUEUE_RESTORED_MESSAGE, Placeholder.unparsed("mode", mode.getFriendlyName())));
                },
                throwable -> {
                    Status status = StatusProto.fromThrowable(throwable);
                    if (status.getCode() == Code.NOT_FOUND_VALUE) return; // Player is not in a queue

                    LOGGER.error("Failed to get player queue info for player " + playerId, throwable);
                }
        ), ForkJoinPool.commonPool());
    }
}
