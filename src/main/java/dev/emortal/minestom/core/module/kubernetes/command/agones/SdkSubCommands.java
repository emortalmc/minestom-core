package dev.emortal.minestom.core.module.kubernetes.command.agones;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import io.grpc.stub.StreamObserver;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.CommandContext;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public final class SdkSubCommands {

    private static Component generateMessage(String sdk, String method, AgonesCommand.RequestStatus status, String message) {
        var text = "Agones >> [%s.%s] (%s) %s".formatted(sdk, method, status.name(), message);
        return Component.text(text, status.getColor());
    }

    private final SDKGrpc.SDKStub sdk;

    public SdkSubCommands(@NotNull SDKGrpc.SDKStub sdk) {
        this.sdk = sdk;
    }

    private <T> StreamObserver<T> createCallback(CommandSender sender, String sdkMethod, Function<T, String> nextMessageProvider) {
        return new StreamObserver<>() {
            @Override
            public void onNext(@NotNull T value) {
                String nextMessage = nextMessageProvider.apply(value);
                var response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.NEXT, nextMessage);
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                var response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                var response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        };
    }

    public void executeGetGameServer(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.getGameServer(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "GetGameServer", Object::toString));
    }

    public void executeReserve(@NotNull CommandSender sender, @NotNull CommandContext context) {
        Duration duration = context.get("duration");
        var agonesDuration = AgonesSDKProto.Duration.newBuilder().setSeconds(duration.getSeconds()).build();

        this.sdk.reserve(agonesDuration, this.createCallback(sender, "Reserve", v -> "Reserved for %s seconds".formatted(duration.getSeconds())));
    }

    public void executeAllocate(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.allocate(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "Allocate", v -> "Allocated"));
    }

    public void executeSetAnnotation(@NotNull CommandSender sender, @NotNull CommandContext context) {
        String key = context.get("key");
        String value = context.get("metaValue");

        var keyValue = AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build();
        this.sdk.setAnnotation(keyValue, this.createCallback(sender, "SetAnnotation", v -> "Set annotation %s to %s".formatted(key, v)));
    }

    public void executeSetLabel(@NotNull CommandSender sender, @NotNull CommandContext context) {
        String key = context.get("key");
        String value = context.get("metaValue");

        var keyValue = AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build();
        this.sdk.setLabel(keyValue, this.createCallback(sender, "SetLabel", v -> "Set label %s to %s".formatted(key, v)));
    }

    public void executeShutdown(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.shutdown(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "Shutdown", v -> "Shutdown"));
    }

    public void executeWatchGameserver(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "WatchGameServer", Object::toString));
    }
}
