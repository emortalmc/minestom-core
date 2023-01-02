package dev.emortal.minestom.core.module.permissions;

import dev.emortal.api.service.PermissionServiceGrpc;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "permissions", required = true)
public class PermissionModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionModule.class);

    private static final boolean ENABLED;

    static {
        String portString = System.getenv("PERMISSION_SVC_PORT");
        ENABLED = Environment.isProduction() || portString != null;
    }

    private PermissionCache permissionCache;

    public PermissionModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        if (!ENABLED) {
            LOGGER.warn("Permission service is not enabled, all players will be granted all permissions.");

            this.eventNode.addListener(PlayerLoginEvent.class, event -> event.getPlayer().addPermission(new AllPermission()));
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

    private static class AllPermission extends Permission {
        public AllPermission() {
            super("unused");
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Permission;
        }
    }
}
