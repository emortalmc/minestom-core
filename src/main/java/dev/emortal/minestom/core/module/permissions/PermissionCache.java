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

import java.util.Collection;
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

        eventNode.addListener(PlayerDisconnectEvent.class, this::onDisconnect)
                .addListener(PlayerLoginEvent.class, this::onLogin);

        this.loadRoles();
    }

    private void loadRoles() {
        try {
            PermissionProto.GetAllRolesResponse response = this.permissionService.getAllRoles(PermissionProto.GetAllRolesRequest.getDefaultInstance()).get();

            for (Role role : response.getRolesList()) {
                this.roleCache.put(
                        role.getId(),
                        new CachedRole(
                                role.getId(),
                                role.getPermissionsList().stream()
                                        .filter(node -> node.getState() == PermissionNode.PermissionState.ALLOW)
                                        .map(protoNode -> new Permission(protoNode.getNode()))
                                        .collect(Collectors.toCollection(Sets::newConcurrentHashSet)),
                                role.getPriority(), role.getDisplayPrefix(), role.getDisplayName()
                        )
                );
            }
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("Couldn't load roles", e);
        }
    }

    /**
     * This method is blocking
     *
     * @param player the player to load
     */
    public void loadUser(@NotNull Player player) {
        try {
            PermissionProto.PlayerRolesResponse result = this.permissionService.getPlayerRoles(
                    PermissionProto.GetPlayerRolesRequest.newBuilder().setPlayerId(player.getUuid().toString()).build()
            ).get();

            Set<String> roleIds = Sets.newConcurrentHashSet(result.getRoleIdsList());
            User user = new User(player.getUuid(), roleIds, this.determineActivePrefix(roleIds), this.determineActiveName(roleIds));
            this.userCache.put(player.getUuid(), user);

            Set<Permission> permissions = new HashSet<>();
            for (String roleId : roleIds) {
                CachedRole role = this.roleCache.get(roleId);
                if (role == null) continue;

                permissions.addAll(role.getPermissions());
            }
            player.getAllPermissions().clear();
            player.getAllPermissions().addAll(permissions);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, CachedRole> getRoleCache() {
        return roleCache;
    }

    public Map<UUID, User> getUserCache() {
        return userCache;
    }

    public Optional<CachedRole> getRole(String id) {
        return Optional.ofNullable(this.roleCache.get(id));
    }

    public Optional<User> getUser(UUID id) {
        return Optional.ofNullable(this.userCache.get(id));
    }

    public void addRole(Role roleResponse) {
        CachedRole role = new CachedRole(
                roleResponse.getId(),
                Sets.newConcurrentHashSet(roleResponse.getPermissionsList().stream()
                        .filter(node -> node.getState() == PermissionNode.PermissionState.ALLOW)
                        .map(protoNode -> new Permission(protoNode.getNode()))
                        .collect(Collectors.toSet())),
                roleResponse.getPriority(), roleResponse.getDisplayPrefix(), roleResponse.getDisplayName()
        );

        this.roleCache.put(roleResponse.getId(), role);
    }

    public Component determineActivePrefix(Collection<String> roleIds) {
        int currentPriority = 0;
        Component currentPrefix = null;
        for (CachedRole role : this.roleCache.values()) {
            if (role.getDisplayPrefix() != null && roleIds.contains(role.getId())) {
                if (role.getPriority() > currentPriority) {
                    currentPriority = role.getPriority();
                    currentPrefix = role.getDisplayPrefix();
                }
            }
        }
        return currentPrefix;
    }

    public String determineActiveName(Collection<String> roleIds) {
        int currentPriority = 0;
        String currentActiveName = null;

        for (CachedRole role : this.roleCache.values()) {
            if (role.getDisplayName() != null && roleIds.contains(role.getId())) {
                if (role.getPriority() > currentPriority) {
                    currentPriority = role.getPriority();
                    currentActiveName = role.getDisplayName();
                }
            }
        }
        return currentActiveName;
    }

    public void onDisconnect(PlayerDisconnectEvent event) {
        this.userCache.remove(event.getPlayer().getUuid());
    }

    public void onLogin(PlayerLoginEvent event) {
        this.loadUser(event.getPlayer());
    }

    public static final class User {
        private final UUID id;
        private final Set<String> roleIds;

        private Component displayPrefix;
        private String displayName;

        public User(UUID id, Set<String> roleIds, Component displayPrefix, String displayName) {
            this.id = id;
            this.roleIds = roleIds;
            this.displayPrefix = displayPrefix;
            this.displayName = displayName;
        }

        public UUID getId() {
            return this.id;
        }

        public Set<String> getRoleIds() {
            return this.roleIds;
        }

        public Component getDisplayPrefix() {
            return this.displayPrefix;
        }

        public void setDisplayPrefix(Component displayPrefix) {
            this.displayPrefix = displayPrefix;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static final class CachedRole implements Comparable<CachedRole> {
        private final String id;
        private final Set<Permission> permissions;

        private int priority;
        private Component displayPrefix;
        private String displayName;

        public CachedRole(String id, Set<Permission> permissions, int priority, String displayPrefix, String displayName) {
            this.id = id;
            this.permissions = permissions;
            this.priority = priority;
            this.displayPrefix = MiniMessage.miniMessage().deserialize(displayPrefix);
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
            return permissions;
        }

        public int getPriority() {
            return this.priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public Component getDisplayPrefix() {
            return this.displayPrefix;
        }

        public void setDisplayPrefix(String displayPrefix) {
            this.displayPrefix = MiniMessage.miniMessage().deserialize(displayPrefix);
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public Component getFormattedDisplayName(String username) {
            return MiniMessage.miniMessage().deserialize(this.displayName, Placeholder.unparsed("username", username));
        }
    }
}
