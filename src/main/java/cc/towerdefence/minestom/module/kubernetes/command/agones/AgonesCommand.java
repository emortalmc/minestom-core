package cc.towerdefence.minestom.module.kubernetes.command.agones;

import cc.towerdefence.minestom.module.kubernetes.KubernetesModule;
import cc.towerdefence.minestom.module.kubernetes.command.agones.subs.SdkSubs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentTime;

public class AgonesCommand extends Command {
    private final KubernetesModule kubernetesModule;

    public AgonesCommand(KubernetesModule kubernetesModule) {
        super("magones");

        this.kubernetesModule = kubernetesModule;

//        AlphaSubs alphaSubs = new AlphaSubs();
//        this.addSyntax(alphaSubs::executeSetCapacity, new ArgumentLiteral("set"), new ArgumentLiteral("capacity"), new ArgumentInteger("value"));
//        this.addSyntax(alphaSubs::executeGetConnectedPlayers, new ArgumentLiteral("get"), new ArgumentLiteral("connected"), new ArgumentLiteral("players"));
//        this.addSyntax(alphaSubs::executeIsPlayerConnected, new ArgumentLiteral("is"), new ArgumentLiteral("connected"), new ArgumentUUID("playerId"));
//        this.addSyntax(alphaSubs::executePlayerConnect, new ArgumentLiteral("set"), new ArgumentLiteral("connected"), new ArgumentUUID("playerId"));
//        this.addSyntax(alphaSubs::executePlayerDisconnect, new ArgumentLiteral("set"), new ArgumentLiteral("disconnected"), new ArgumentUUID("playerId"));

        SdkSubs sdkSubs = new SdkSubs(this.kubernetesModule.getSdk());
        this.addSyntax(sdkSubs::executeGetGameServer, new ArgumentLiteral("get"), new ArgumentLiteral("gameserver"));
        this.addSyntax(sdkSubs::executeReserve, new ArgumentLiteral("reserve"), new ArgumentTime("duration"));
        this.addSyntax(sdkSubs::executeAllocate, new ArgumentLiteral("allocate"));
        this.addSyntax(sdkSubs::executeSetAnnotation, new ArgumentLiteral("set"), new ArgumentLiteral("annotation"), new ArgumentString("key"), new ArgumentString("value"));
        this.addSyntax(sdkSubs::executeSetLabel, new ArgumentLiteral("set"), new ArgumentLiteral("label"), new ArgumentString("key"), new ArgumentString("value"));
        this.addSyntax(sdkSubs::executeShutdown, new ArgumentLiteral("shutdown"));
        this.addSyntax(sdkSubs::executeWatchGameserver, new ArgumentLiteral("watch"), new ArgumentLiteral("gameserver"));
    }

    public static Component generateMessage(String sdk, String method, RequestStatus status, String message) {
        String text = "Agones >> [%s.%s] (%s) %s".formatted(sdk, method, status.name(), message);

        return Component.text(text, status.getColor());
    };

    public enum RequestStatus {
        NEXT(NamedTextColor.GREEN),
        ERROR(NamedTextColor.RED),
        COMPLETED(NamedTextColor.AQUA);

        private final NamedTextColor color;

        RequestStatus(NamedTextColor color) {
            this.color = color;
        }

        public NamedTextColor getColor() {
            return this.color;
        }
    }
}
