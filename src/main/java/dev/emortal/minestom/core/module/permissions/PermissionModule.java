package dev.emortal.minestom.core.module.permissions;

import dev.emortal.api.modules.annotation.Dependency;
import dev.emortal.api.modules.annotation.ModuleData;
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

@ModuleData(name = "permissions", dependencies = {@Dependency(name = "messaging", required = false)})
public final class PermissionModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionModule.class);

    private static final boolean ENABLED = Environment.isProduction() || GrpcStubCollection.getPermissionService().isPresent();
    // Grants all permissions if the permission service is not available
    private static final boolean GRANT_ALL_PERMISSIONS = Boolean.parseBoolean(System.getenv("GRANT_ALL_PERMISSIONS"));

    private @Nullable PermissionCache permissionCache;

    public PermissionModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        if (!ENABLED) {
            if (GRANT_ALL_PERMISSIONS) {
                LOGGER.warn("Permission service is not available, granting all permissions");
                this.eventNode.addListener(PlayerLoginEvent.class, event -> event.getPlayer().addPermission(new Permission("*")));
            } else {
                LOGGER.warn("Permission service is not available, denying all permissions");
            }

            return true;
        }

        PermissionService service = GrpcStubCollection.getPermissionService().orElse(null);
        if (service == null) {
            LOGGER.warn("Permission service is not available. Permissions will not work correctly.");
            return false;
        }

        this.permissionCache = new PermissionCache(service, this.eventNode);

        MessagingModule messagingModule = this.getOptionalModule(MessagingModule.class);
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
