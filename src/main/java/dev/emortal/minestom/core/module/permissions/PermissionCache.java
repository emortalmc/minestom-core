package dev.emortal.minestom.core.module.permissions;

import com.google.common.collect.Sets;
import dev.emortal.api.grpc.permission.PermissionProto;
import dev.emortal.api.grpc.permission.PermissionServiceGrpc;
import dev.emortal.api.model.permission.PermissionNode;
import dev.emortal.api.model.permission.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class PermissionCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionCache.class);

    private final Map<String, CachedRole> roleCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<UUID, User> userCache = new ConcurrentHashMap<>();

    private final PermissionServiceGrpc.PermissionServiceFutureStub permissionService;

    public PermissionCache(PermissionServiceGrpc.PermissionServiceFutureStub permissionService, EventNode<Event> eventNode) {
        this.permissionService = permissionService;

        eventNode.addListener(PlayerDisconnectEvent.class, this::onDisconnect);
        eventNode.addListener(PlayerLoginEvent.class, this::onLogin);

        loadRoles();
    }

    private void loadRoles() {
        try {
            final var response = permissionService.getAllRoles(PermissionProto.GetAllRolesRequest.getDefaultInstance()).get();

            for (final Role role : response.getRolesList()) {
                roleCache.put(
                        role.getId(),
                        new CachedRole(
                                role.getId(),
                                role.getPermissionsList().stream()
                                        .filter(node -> node.getState() == PermissionNode.PermissionState.ALLOW)
                                        .map(protoNode -> new Permission(protoNode.getNode()))
                                        .collect(Collectors.toCollection(Sets::newConcurrentHashSet)),
                                role.getPriority(), role.getDisplayName()
                        )
                );
            }
        } catch (final InterruptedException | ExecutionException exception) {
            LOGGER.error("Couldn't load roles", exception);
        }
    }

    /**
     * This method is blocking
     *
     * @param player the player to load
     */
    public void loadUser(@NotNull Player player) {
        try {
            final var request = PermissionProto.GetPlayerRolesRequest.newBuilder().setPlayerId(player.getUuid().toString()).build();

            final Set<String> roleIds = Sets.newConcurrentHashSet(permissionService.getPlayerRoles(request).get().getRoleIdsList());
            final User user = new User(player.getUuid(), roleIds);
            userCache.put(player.getUuid(), user);

            final Set<Permission> permissions = new HashSet<>();
            for (final String roleId : roleIds) {
                final CachedRole role = roleCache.get(roleId);
                if (role == null) continue;

                permissions.addAll(role.getPermissions());
            }
            player.getAllPermissions().clear();
            player.getAllPermissions().addAll(permissions);
        } catch (final InterruptedException | ExecutionException exception) {
            throw new RuntimeException(exception);
        }
    }

    public Map<String, CachedRole> getRoleCache() {
        return roleCache;
    }

    public Map<UUID, User> getUserCache() {
        return userCache;
    }

    public Optional<CachedRole> getRole(String id) {
        return Optional.ofNullable(roleCache.get(id));
    }

    public Optional<User> getUser(UUID id) {
        return Optional.ofNullable(userCache.get(id));
    }

    public void addRole(@NotNull Role roleResponse) {
        final CachedRole role = new CachedRole(
                roleResponse.getId(),
                Sets.newConcurrentHashSet(roleResponse.getPermissionsList().stream()
                        .filter(node -> node.getState() == PermissionNode.PermissionState.ALLOW)
                        .map(protoNode -> new Permission(protoNode.getNode()))
                        .collect(Collectors.toSet())),
                roleResponse.getPriority(), roleResponse.getDisplayName()
        );

        roleCache.put(roleResponse.getId(), role);
    }

    public void onDisconnect(PlayerDisconnectEvent event) {
        userCache.remove(event.getPlayer().getUuid());
    }

    public void onLogin(PlayerLoginEvent event) {
        loadUser(event.getPlayer());
    }

    public record User(UUID id, Set<String> roleIds) {
    }

    public static final class CachedRole implements Comparable<CachedRole> {

        private final String id;
        private final Set<Permission> permissions;

        private int priority;
        private Component displayPrefix;
        private String displayName;

        public CachedRole(@NotNull String id, @NotNull Set<Permission> permissions, int priority, @NotNull String displayName) {
            this.id = id;
            this.permissions = permissions;
            this.priority = priority;
            this.displayName = displayName;
        }

        @Override
        public int compareTo(@NotNull PermissionCache.CachedRole o) {
            return Integer.compare(priority, o.priority);
        }

        public String getId() {
            return id;
        }

        public Set<Permission> getPermissions() {
            return permissions;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public Component getDisplayPrefix() {
            return displayPrefix;
        }

        public void setDisplayPrefix(String displayPrefix) {
            this.displayPrefix = MiniMessage.miniMessage().deserialize(displayPrefix);
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public Component getFormattedDisplayName(String username) {
            return MiniMessage.miniMessage().deserialize(displayName, Placeholder.unparsed("username", username));
        }
    }
}
