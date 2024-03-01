package dev.emortal.minestom.core.module.permissions;

import com.google.common.collect.Sets;
import dev.emortal.api.grpc.permission.PermissionProto.PlayerRolesResponse;
import dev.emortal.api.model.permission.PermissionNode;
import dev.emortal.api.model.permission.Role;
import dev.emortal.api.service.permission.PermissionService;
import io.grpc.StatusRuntimeException;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PermissionCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionCache.class);

    private final @NotNull PermissionService permissionService;

    private final Map<String, CachedRole> roleCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<UUID, User> userCache = new ConcurrentHashMap<>();

    public PermissionCache(@NotNull PermissionService permissionService, @NotNull EventNode<Event> eventNode) {
        this.permissionService = permissionService;

        eventNode.addListener(PlayerDisconnectEvent.class, this::onDisconnect);
        eventNode.addListener(AsyncPlayerConfigurationEvent.class, this::onLogin);

        this.loadRoles();
    }

    private void loadRoles() {
        List<Role> roles;
        try {
            roles = this.permissionService.getAllRoles();
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to load all roles", exception);
            return;
        }

        for (Role role : roles) {
            this.addRole(role);
        }
    }

    /**
     * This method is blocking
     *
     * @param player the player to load
     */
    private void loadUser(@NotNull Player player) {
        PlayerRolesResponse response;
        try {
            response = this.permissionService.getPlayerRoles(player.getUuid());
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to get roles for '{}'", player.getUsername(), exception);
            return;
        }

        Set<String> roleIds = Sets.newConcurrentHashSet(response.getRoleIdsList());
        User user = new User(player.getUuid(), roleIds);
        this.userCache.put(player.getUuid(), user);

        Set<Permission> permissions = new HashSet<>();
        for (String roleId : roleIds) {
            CachedRole role = this.roleCache.get(roleId);
            if (role == null) continue;

            permissions.addAll(role.permissions());
        }

        player.getAllPermissions().clear();
        player.getAllPermissions().addAll(permissions);
    }

    private void updateUserPermissions(@NotNull User user) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(user.id());
        if (player == null) {
            LOGGER.error("Couldn't find player with id {}", user.id());
            return;
        }

        Set<Permission> permissions = this.calculatePerms(user.roleIds());
        this.userCache.put(user.id(), user);
        player.getAllPermissions().clear();
        player.getAllPermissions().addAll(permissions);

        player.refreshCommands();
    }

    private @NotNull Set<Permission> calculatePerms(@NotNull Set<String> roleIds) {
        Set<Permission> permissions = new HashSet<>();
        for (String roleId : roleIds) {
            CachedRole role = this.roleCache.get(roleId);
            if (role == null) {
                LOGGER.warn("Couldn't find role with id {}", roleId);
                continue;
            }

            permissions.addAll(role.permissions());
        }
        return permissions;
    }

    public Optional<CachedRole> getRole(@NotNull String id) {
        return Optional.ofNullable(this.roleCache.get(id));
    }

    public Optional<User> getUser(@NotNull UUID id) {
        return Optional.ofNullable(this.userCache.get(id));
    }

    /**
     * Adds a role to the cache OR overrides one that already exists.
     *
     * @param roleResponse the role to add (from a proto message)
     */
    void addRole(@NotNull Role roleResponse) {
        CachedRole role = CachedRole.fromRole(roleResponse);
        this.roleCache.put(roleResponse.getId(), role);

        for (User user : this.userCache.values()) {
            if (user.roleIds().contains(roleResponse.getId())) {
                this.updateUserPermissions(user);
            }
        }
    }

    void removeRole(@NotNull String id) {
        for (User user : this.userCache.values()) {
            if (user.roleIds().contains(id)) this.removeRoleFromUser(user.id(), id);
        }

        this.roleCache.remove(id);
    }

    void addRoleToUser(@NotNull UUID userId, @NotNull String roleId) {
        User user = this.userCache.get(userId);
        if (user == null) {
            LOGGER.error("Couldn't find user with id {}", userId);
            return;
        }

        user.roleIds().add(roleId);
        this.updateUserPermissions(user);
    }

    void removeRoleFromUser(@NotNull UUID userId, @NotNull String roleId) {
        User user = this.userCache.get(userId);
        if (user == null) {
            LOGGER.error("Couldn't find user with id {}", userId);
            return;
        }

        user.roleIds().remove(roleId);
        this.updateUserPermissions(user);
    }

    private void refreshCommands(@NotNull UUID playerId) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId);
        if (player == null) {
            LOGGER.error("Couldn't find player with id {}", playerId);
            return;
        }

        player.refreshCommands();
    }

    private void onDisconnect(@NotNull PlayerDisconnectEvent event) {
        this.userCache.remove(event.getPlayer().getUuid());
    }

    private void onLogin(@NotNull AsyncPlayerConfigurationEvent event) {
        this.loadUser(event.getPlayer());
    }

    public record User(@NotNull UUID id, @NotNull Set<String> roleIds) {
    }

    public record CachedRole(@NotNull String id, @NotNull Set<Permission> permissions, int priority,
                             @NotNull String displayName) implements Comparable<CachedRole> {

        static CachedRole fromRole(@NotNull Role role) {
            return new CachedRole(
                    role.getId(),
                    role.getPermissionsList().stream()
                            .filter(node -> node.getState() == PermissionNode.PermissionState.ALLOW)
                            .map(protoNode -> new Permission(protoNode.getNode()))
                            .collect(Collectors.toCollection(Sets::newConcurrentHashSet)),
                    role.getPriority(),
                    role.getDisplayName()
            );
        }

        @Override
        public int compareTo(@NotNull PermissionCache.CachedRole o) {
            return Integer.compare(this.priority, o.priority);
        }
    }
}
