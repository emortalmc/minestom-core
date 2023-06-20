package dev.emortal.minestom.core.module.permissions;

import dev.emortal.api.grpc.permission.PermissionServiceGrpc;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.ModuleEnvironment;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.MinestomModule;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@ModuleData(name = "permissions", required = true)
public final class PermissionModule extends MinestomModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionModule.class);

    private static final boolean ENABLED;
    // Grants all permissions if the permission service is not available
    private static final boolean GRANT_ALL_PERMISSIONS;

    static {
        ENABLED = Environment.isProduction() || GrpcStubCollection.getPermissionService().isPresent();

        String grantAllString = System.getenv("GRANT_ALL_PERMISSIONS");
        GRANT_ALL_PERMISSIONS = Boolean.parseBoolean(grantAllString);
    }

    private PermissionCache permissionCache;

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

        PermissionServiceGrpc.PermissionServiceFutureStub permissionService =
                GrpcStubCollection.getPermissionService().orElse(null);

        this.permissionCache = new PermissionCache(permissionService, this.eventNode);

        return true;
    }

    @Override
    public void onUnload() {

    }

    public PermissionCache getPermissionCache() {
        return this.permissionCache;
    }

    public static class AllPermissionPlayer extends Player {

        public AllPermissionPlayer(@NotNull UUID uuid, @NotNull String username, @NotNull PlayerConnection playerConnection) {
            super(uuid, username, playerConnection);
        }

        @Override
        public boolean hasPermission(@NotNull String permissionName) {
            return super.hasPermission(new Permission(permissionName));
        }
    }

    public static class AllPermission extends Permission {

        public AllPermission() {
            super("unused");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Permission; // Just check if it is a Permission type
        }
    }
}
