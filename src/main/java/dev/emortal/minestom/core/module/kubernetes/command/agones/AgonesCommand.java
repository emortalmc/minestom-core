package dev.emortal.minestom.core.module.kubernetes.command.agones;

import dev.agones.sdk.SDKGrpc;
import dev.emortal.minestom.core.utils.command.ExtraConditions;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentTime;
import org.jetbrains.annotations.NotNull;

public final class AgonesCommand extends Command {

    public AgonesCommand(@NotNull SDKGrpc.SDKStub sdk, dev.agones.sdk.beta.SDKGrpc.SDKStub betaSdk) {
        super("magones");
        this.setCondition(ExtraConditions.hasPermission("command.agones"));

        var sdkSubs = new SdkSubCommands(sdk, betaSdk);

        // /magones get gameserver
        this.addSyntax(sdkSubs::executeGetGameServer, new ArgumentLiteral("get"), new ArgumentLiteral("gameserver"));

        // /magones get count [id]
        this.addSyntax(sdkSubs::executeListCounters, new ArgumentLiteral("get"), new ArgumentLiteral("counters"));

        // /magones get list [id]
        this.addSyntax(sdkSubs::executeListLists, new ArgumentLiteral("get"), new ArgumentLiteral("list"));
        this.addSyntax(sdkSubs::executeGetList, new ArgumentLiteral("get"), new ArgumentLiteral("list"), new ArgumentString("id"));

        var set = new ArgumentLiteral("set");
        var key = new ArgumentString("key");
        var value = new ArgumentString("value");
        // /magones set annotation <key> <value>
        this.addSyntax(sdkSubs::executeSetAnnotation, new ArgumentLiteral("set"), new ArgumentLiteral("annotation"), key, value);
        // /magones set label <key> <value>
        this.addSyntax(sdkSubs::executeSetLabel, new ArgumentLiteral("set"), new ArgumentLiteral("label"), key, value);

        // /magones shutdown
        this.addSyntax(sdkSubs::executeShutdown, new ArgumentLiteral("shutdown"));
        // /magones watch gameserver
        this.addSyntax(sdkSubs::executeWatchGameserver, new ArgumentLiteral("watch"), new ArgumentLiteral("gameserver"));
    }

    public enum RequestStatus {

        NEXT(NamedTextColor.GREEN),
        ERROR(NamedTextColor.RED),
        COMPLETED(NamedTextColor.AQUA);

        private final NamedTextColor color;

        RequestStatus(NamedTextColor color) {
            this.color = color;
        }

        public @NotNull NamedTextColor getColor() {
            return this.color;
        }
    }
}
