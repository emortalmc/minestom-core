package cc.towerdefence.minestom.module.permissions;

import cc.towerdefence.api.service.PermissionServiceGrpc;
import cc.towerdefence.minestom.module.Module;
import cc.towerdefence.minestom.module.ModuleData;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "permissions", required = true)
public class PermissionModule extends Module {
    private PermissionCache permissionCache;

    protected PermissionModule(@NotNull EventNode<Event> eventNode) {
        super(eventNode);
    }

    @Override
    public boolean onLoad() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("permission.towerdefence.svc", 9090)
                .defaultLoadBalancingPolicy("round_robin")
                .usePlaintext()
                .build();
        PermissionServiceGrpc.PermissionServiceFutureStub permissionService = PermissionServiceGrpc.newFutureStub(channel);

        this.permissionCache = new PermissionCache(permissionService, this.eventNode);

        return false;
    }

    @Override
    public void onUnload() {

    }

    public PermissionCache getPermissionCache() {
        return permissionCache;
    }
}
