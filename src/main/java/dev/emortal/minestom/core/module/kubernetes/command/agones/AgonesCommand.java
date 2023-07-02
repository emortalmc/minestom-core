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

    public AgonesCommand(@NotNull SDKGrpc.SDKStub sdk) {
        super("magones");
        this.setCondition(ExtraConditions.hasPermission("command.agones"));

        var sdkSubs = new SdkSubCommands(sdk);

        var get = new ArgumentLiteral("get");
        var set = new ArgumentLiteral("set");
        var key = new ArgumentString("key");
        var metaValue = new ArgumentString("metaValue");

        this.addSyntax(sdkSubs::executeGetGameServer, get, new ArgumentLiteral("gameserver"));
        this.addSyntax(sdkSubs::executeReserve, new ArgumentLiteral("reserve"), new ArgumentTime("duration"));
        this.addSyntax(sdkSubs::executeAllocate, new ArgumentLiteral("allocate"));
        this.addSyntax(sdkSubs::executeSetAnnotation, set, new ArgumentLiteral("annotation"), key, metaValue);
        this.addSyntax(sdkSubs::executeSetLabel, set, new ArgumentLiteral("label"), key, metaValue);
        this.addSyntax(sdkSubs::executeShutdown, new ArgumentLiteral("shutdown"));
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
