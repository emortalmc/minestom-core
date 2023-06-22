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

public final class MatchmakingSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatchmakingSessionManager.class);

    private static final String QUEUE_RESTORED_MESSAGE = "<green>Your queue for <mode> has been transferred!</green>";
    private static final String QUEUE_RESTORE_FAILED_MESSAGE = "<red>Your queue for <mode> could not be transferred! Please tell a staff member.</red>";

    private final MatchmakerGrpc.MatchmakerFutureStub matchmaker;
    private final TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator;

    private final Map<UUID, MatchmakingSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketCache = new ConcurrentHashMap<>();

    private final Map<String, GameModeConfig> configs = new ConcurrentHashMap<>();

    // TODO: note that tickets technically memory leak but it's so small and cleaned up when the ticket is deleted.
    public MatchmakingSessionManager(@NotNull EventNode<Event> eventNode, @NotNull MatchmakerGrpc.MatchmakerFutureStub matchmaker,
                                     @NotNull MessagingModule messaging, @NotNull GameModeCollection gameModeCollection,
                                     @NotNull TriFunction<Player, GameModeConfig, Ticket, MatchmakingSession> sessionCreator) {
        this.matchmaker = matchmaker;
        this.sessionCreator = sessionCreator;

        // Get game modes
        gameModeCollection.getAllConfigs(this::handleConfigUpdate).forEach(config -> configs.put(config.getId(), config));

        final ConnectionManager connectionManager = MinecraftServer.getConnectionManager();

        eventNode.addListener(PlayerLoginEvent.class, this::handlePlayerLogin);

        eventNode.addListener(PlayerDisconnectEvent.class, event -> {
            final UUID playerId = event.getPlayer().getUuid();
            final MatchmakingSession session = sessions.remove(playerId);
            if (session == null) return;

            destroySession(session);
        });

        messaging.addListener(TicketCreatedMessage.class, message -> {
            final Ticket ticket = message.getTicket();

            boolean shouldCache = false;

            for (final String playerId : ticket.getPlayerIdsList()) {
                final UUID uuid = UUID.fromString(playerId);
                final Player player = connectionManager.getPlayer(uuid);
                if (player == null) continue;

                final GameModeConfig gameMode = configs.get(ticket.getGameModeId());
                final MatchmakingSession session = sessionCreator.apply(player, gameMode, ticket);
                sessions.put(uuid, session);
                shouldCache = true;
            }

            if (shouldCache) {
                ticketCache.put(ticket.getId(), ticket);
            }
        });

        messaging.addListener(TicketDeletedMessage.class, message -> {
            final Ticket ticket = message.getTicket();

            final Ticket removedTicket = ticketCache.remove(ticket.getId());
            if (removedTicket == null) return; // If the ticket is not cached, there should be no sessions with it.

            for (final String playerId : ticket.getPlayerIdsList()) {
                final UUID uuid = UUID.fromString(playerId);
                final Player player = connectionManager.getPlayer(uuid);
                if (player == null) continue;

                final MatchmakingSession session = sessions.remove(uuid);
                if (session == null) continue;

                final MatchmakingSession.DeleteReason deleteReason = switch (message.getReason()) {
                    case MATCH_CREATED -> MatchmakingSession.DeleteReason.MATCH_CREATED;
                    case MANUAL_DEQUEUE -> MatchmakingSession.DeleteReason.MANUAL_DEQUEUE;
                    case GAME_MODE_DELETED -> MatchmakingSession.DeleteReason.GAME_MODE_DELETED;
                    default -> MatchmakingSession.DeleteReason.UNKNOWN;
                };

                session.notifyDeletion(deleteReason);
                destroySession(session);
            }
        });

        // NOTE: This logic requires on this method being run synchronously.
        // NOTE 2: This logic is only for players leaving/joining a ticket. It doesn't need anything else.
        messaging.addListener(TicketUpdatedMessage.class, message -> {
            final Ticket newTicket = message.getNewTicket();
            final Ticket oldTicket = ticketCache.get(newTicket.getId());

            final GameModeConfig gameMode = configs.get(newTicket.getGameModeId());

            // Perform adding operations and update existing MatchmakingSessions
            for (final String playerId : newTicket.getPlayerIdsList()) {
                MatchmakingSession session = sessions.get(UUID.fromString(playerId));
                if (session != null) {
                    session.setTicket(newTicket);
                    continue;
                }

                final UUID uuid = UUID.fromString(playerId);
                final Player player = connectionManager.getPlayer(uuid);
                if (player == null) continue;

                session = sessionCreator.apply(player, gameMode, newTicket);
                sessions.put(uuid, session);
            }

            // Calculate removed if oldTicket is not null
            if (oldTicket != null) {
                for (final String playerId : oldTicket.getPlayerIdsList()) {
                    if (newTicket.getPlayerIdsList().contains(playerId)) continue;

                    final UUID uuid = UUID.fromString(playerId);
                    final Player player = connectionManager.getPlayer(uuid);
                    if (player == null) continue;

                    final MatchmakingSession session = sessions.remove(uuid);
                    if (session == null) continue;

                    session.notifyDeletion(MatchmakingSession.DeleteReason.UNKNOWN);
                    destroySession(session);
                }
            }

            ticketCache.put(newTicket.getId(), newTicket);
        });

        messaging.addListener(PendingMatchCreatedMessage.class, message -> {
            final PendingMatch pendingMatch = message.getPendingMatch();
            for (final String ticketId : pendingMatch.getTicketIdsList()) {
                final Ticket ticket = ticketCache.get(ticketId);
                if (ticket == null) continue;

                for (final String playerId : ticket.getPlayerIdsList()) {
                    final UUID uuid = UUID.fromString(playerId);

                    final MatchmakingSession session = sessions.get(uuid);
                    if (session == null) continue;

                    session.onPendingMatchCreate(pendingMatch);
                }
            }
        });

        messaging.addListener(PendingMatchUpdatedMessage.class, message -> {
            final PendingMatch pendingMatch = message.getPendingMatch();
            for (final String ticketId : pendingMatch.getTicketIdsList()) {
                final Ticket ticket = ticketCache.get(ticketId);
                if (ticket == null) continue;

                for (final String playerId : ticket.getPlayerIdsList()) {
                    final UUID uuid = UUID.fromString(playerId);

                    final MatchmakingSession session = sessions.get(uuid);
                    if (session == null) continue;

                    session.onPendingMatchUpdate(pendingMatch);
                }
            }
        });

        messaging.addListener(PendingMatchDeletedMessage.class, message -> {
            // Ignore this message as it's handled by the MatchCreatedMessage (and the proxy will message them :D)
            if (message.getReason() == PendingMatchDeletedMessage.Reason.MATCH_CREATED) return;

            final PendingMatch pendingMatch = message.getPendingMatch();
            for (final String ticketId : pendingMatch.getTicketIdsList()) {
                final Ticket ticket = ticketCache.get(ticketId);
                if (ticket == null) continue;

                for (final String playerId : ticket.getPlayerIdsList()) {
                    final UUID uuid = UUID.fromString(playerId);

                    final MatchmakingSession session = sessions.get(uuid);
                    if (session == null) continue;

                    session.onPendingMatchCancelled(pendingMatch);
                }
            }
        });

        messaging.addListener(MatchCreatedMessage.class, message -> {
            for (final Ticket ticket : message.getMatch().getTicketsList()) {
                for (final String playerId : ticket.getPlayerIdsList()) {
                    final UUID uuid = UUID.fromString(playerId);

                    final MatchmakingSession session = sessions.get(uuid);
                    if (session == null) continue;

                    session.notifyDeletion(MatchmakingSession.DeleteReason.MATCH_CREATED);
                    destroySession(session);
                }
            }
        });
    }

    private void destroySession(@NotNull MatchmakingSession session) {
        sessions.remove(session.getPlayer().getUuid());
        session.destroy();
    }

    private void handleConfigUpdate(@NotNull ConfigUpdate<GameModeConfig> update) {
        final GameModeConfig config = update.getConfig();

        switch (update.getType()) {
            case CREATE, MODIFY -> configs.put(config.getId(), config);
            case DELETE -> configs.remove(config.getId());
        }
    }

    private void handlePlayerLogin(@NotNull PlayerLoginEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUuid();
        final var infoRequest = GetPlayerQueueInfoRequest.newBuilder().setPlayerId(playerId.toString()).build();

        Futures.addCallback(matchmaker.getPlayerQueueInfo(infoRequest), FunctionalFutureCallback.create(
                response -> {
                    final Ticket ticket = response.getTicket();
                    final GameModeConfig mode = this.configs.get(ticket.getGameModeId());

                    final var modeName = Placeholder.unparsed("mode", mode == null ? ticket.getGameModeId() : mode.getFriendlyName());
                    if (mode == null) {
                        LOGGER.error("Failed to get game mode config for player " + playerId + " with game mode ID " + ticket.getGameModeId());
                        player.sendMessage(MiniMessage.miniMessage().deserialize(QUEUE_RESTORE_FAILED_MESSAGE, modeName));
                        return;
                    }

                    final MatchmakingSession session = sessionCreator.apply(player, mode, ticket);
                    sessions.put(playerId, session);
                    ticketCache.put(ticket.getId(), ticket);

                    player.sendMessage(MiniMessage.miniMessage().deserialize(QUEUE_RESTORED_MESSAGE, modeName));
                },
                throwable -> {
                    final Status status = StatusProto.fromThrowable(throwable);
                    if (status.getCode() == Code.NOT_FOUND_VALUE) return; // Player is not in a queue

                    LOGGER.error("Failed to get player queue info for player " + playerId, throwable);
                }
        ), ForkJoinPool.commonPool());
    }
}
