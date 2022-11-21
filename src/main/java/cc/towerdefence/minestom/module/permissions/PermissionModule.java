package cc.towerdefence.minestom.module.permissions;

import cc.towerdefence.api.service.PermissionServiceGrpc;
import cc.towerdefence.api.utils.GrpcStubCollection;
import cc.towerdefence.minestom.Environment;
import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import cc.towerdefence.minestom.module.ModuleEnvironment;
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
