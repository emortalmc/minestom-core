package dev.emortal.minestom.core.module.permissions;

import dev.emortal.api.message.permission.PlayerRolesUpdateMessage;
import dev.emortal.api.message.permission.RoleUpdateMessage;
import dev.emortal.api.model.permission.Role;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class PermissionUpdateListener {

    private final @NotNull PermissionCache permissionCache;

    public PermissionUpdateListener(@NotNull PermissionCache permissionCache, @NotNull MessagingModule module) {
        this.permissionCache = permissionCache;

        module.addListener(RoleUpdateMessage.class, this::onRoleUpdate);
        module.addListener(PlayerRolesUpdateMessage.class, this::onPlayerRolesUpdate);
    }

    private void onRoleUpdate(@NotNull RoleUpdateMessage message) {
        Role role = message.getRole();
        switch (message.getChangeType()) {
            case CREATE, MODIFY -> this.permissionCache.addRole(role);
            case DELETE -> this.permissionCache.removeRole(role.getId());
        }
    }

    private void onPlayerRolesUpdate(@NotNull PlayerRolesUpdateMessage message) {
        UUID playerId = UUID.fromString(message.getPlayerId());
        String roleId = message.getRoleId();

        switch (message.getChangeType()) {
            case ADD -> this.permissionCache.addRoleToUser(playerId, roleId);
            case REMOVE -> this.permissionCache.removeRoleFromUser(playerId, roleId);
        }
    }
}
