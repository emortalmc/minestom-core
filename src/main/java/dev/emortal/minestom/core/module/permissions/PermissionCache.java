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
import org.jetbrains.annotations.Nullable;
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

    private final PermissionServiceGrpc.PermissionServiceFutureStub permissionService;

    private final Map<String, CachedRole> roleCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<UUID, User> userCache = new ConcurrentHashMap<>();

    public PermissionCache(PermissionServiceGrpc.PermissionServiceFutureStub permissionService, EventNode<Event> eventNode) {
        this.permissionService = permissionService;

        eventNode.addListener(PlayerDisconnectEvent.class, this::onDisconnect);
        eventNode.addListener(PlayerLoginEvent.class, this::onLogin);

        this.loadRoles();
    }

    private void loadRoles() {
        try {
            var response = this.permissionService.getAllRoles(PermissionProto.GetAllRolesRequest.getDefaultInstance()).get();

            for (Role role : response.getRolesList()) {
                this.roleCache.put(role.getId(), CachedRole.fromRole(role));
            }
        } catch (InterruptedException | ExecutionException exception) {
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
            var request = PermissionProto.GetPlayerRolesRequest.newBuilder().setPlayerId(player.getUuid().toString()).build();
            Set<String> roleIds = Sets.newConcurrentHashSet(this.permissionService.getPlayerRoles(request).get().getRoleIdsList());

            var user = new User(player.getUuid(), roleIds);
            this.userCache.put(player.getUuid(), user);

            Set<Permission> permissions = new HashSet<>();
            for (var roleId : roleIds) {
                CachedRole role = this.roleCache.get(roleId);
                if (role == null) continue;

                permissions.addAll(role.getPermissions());
            }

            player.getAllPermissions().clear();
            player.getAllPermissions().addAll(permissions);
        } catch (InterruptedException | ExecutionException exception) {
            throw new RuntimeException(exception);
        }
    }

    public @NotNull Map<String, CachedRole> getRoleCache() {
        return this.roleCache;
    }

    public @NotNull Map<UUID, User> getUserCache() {
        return this.userCache;
    }

    public @NotNull Optional<CachedRole> getRole(@NotNull String id) {
        return Optional.ofNullable(this.roleCache.get(id));
    }

    public @NotNull Optional<User> getUser(@NotNull UUID id) {
        return Optional.ofNullable(this.userCache.get(id));
    }

    public void addRole(@NotNull Role role) {
        this.roleCache.put(role.getId(), CachedRole.fromRole(role));
    }

    public void onDisconnect(PlayerDisconnectEvent event) {
        this.userCache.remove(event.getPlayer().getUuid());
    }

    public void onLogin(PlayerLoginEvent event) {
        this.loadUser(event.getPlayer());
    }

    public record User(UUID id, Set<String> roleIds) {
    }

    public static final class CachedRole implements Comparable<CachedRole> {

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

        private final String id;
        private final Set<Permission> permissions;

        private int priority;
        private @Nullable Component displayPrefix;
        private String displayName;

        public CachedRole(@NotNull String id, @NotNull Set<Permission> permissions, int priority, @NotNull String displayName) {
            this.id = id;
            this.permissions = permissions;
            this.priority = priority;
            this.displayName = displayName;
        }

        @Override
        public int compareTo(@NotNull PermissionCache.CachedRole o) {
            return Integer.compare(this.priority, o.priority);
        }

        public String getId() {
            return this.id;
        }

        public Set<Permission> getPermissions() {
            return this.permissions;
        }

        public int getPriority() {
            return this.priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public @Nullable Component getDisplayPrefix() {
            return this.displayPrefix;
        }

        public void setDisplayPrefix(@NotNull String displayPrefix) {
            this.displayPrefix = MiniMessage.miniMessage().deserialize(displayPrefix);
        }

        public @NotNull String getDisplayName() {
            return this.displayName;
        }

        public void setDisplayName(@NotNull String displayName) {
            this.displayName = displayName;
        }

        public @NotNull Component getFormattedDisplayName(@NotNull String username) {
            return MiniMessage.miniMessage().deserialize(this.displayName, Placeholder.unparsed("username", username));
        }
    }
}
