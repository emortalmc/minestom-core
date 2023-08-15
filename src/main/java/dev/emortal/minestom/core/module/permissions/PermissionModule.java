package dev.emortal.minestom.core.module.permissions;

import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.service.permission.PermissionService;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.MinestomModule;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "permissions", required = true)
public final class PermissionModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionModule.class);

    // Grants all permissions if the permission service is not available
    private static final boolean GRANT_ALL_PERMISSIONS = Boolean.parseBoolean(System.getenv("GRANT_ALL_PERMISSIONS"));

    private PermissionCache permissionCache;

    public PermissionModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        PermissionService permissionService = GrpcStubCollection.getPermissionService().orElse(null);
        if (!Environment.isProduction() || permissionService == null) {
            if (GRANT_ALL_PERMISSIONS) {
                LOGGER.warn("Permission service is not available, granting all permissions");
                this.eventNode.addListener(PlayerLoginEvent.class, event -> event.getPlayer().addPermission(new Permission("*")));
            } else {
                LOGGER.warn("Permission service is not available, denying all permissions");
            }

            return true;
        }

        this.permissionCache = new PermissionCache(permissionService, this.eventNode);

        MessagingModule messagingModule = this.environment.moduleProvider().getModule(MessagingModule.class);
        if (messagingModule != null) {
            new PermissionUpdateListener(this.permissionCache, messagingModule);
        }

        return true;
    }

    @Override
    public void onUnload() {
    }

    public @Nullable PermissionCache getPermissionCache() {
        return this.permissionCache;
    }
}
