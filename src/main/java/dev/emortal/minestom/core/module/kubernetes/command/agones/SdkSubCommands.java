package dev.emortal.minestom.core.module.kubernetes.command.agones;

import allocation.Allocation;
import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.agones.sdk.beta.BetaAgonesSDKProto;
import io.grpc.stub.StreamObserver;

import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.CommandContext;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public final class SdkSubCommands {

    private static @NotNull Component generateMessage(@NotNull String sdk, @NotNull String method, @NotNull AgonesCommand.RequestStatus status,
                                                      @NotNull String message) {
        String text = "Agones >> [%s.%s] (%s)".formatted(sdk, method, status.name());
        if (!message.isEmpty()) {
            text += "\n" + message;
        }

        return Component.text(text, status.getColor());
    }

    private final @NotNull SDKGrpc.SDKStub sdk;
    private final @NotNull dev.agones.sdk.beta.SDKGrpc.SDKStub betaSdk;

    public SdkSubCommands(@NotNull SDKGrpc.SDKStub sdk, dev.agones.sdk.beta.SDKGrpc.@NotNull SDKStub betaSdk) {
        this.sdk = sdk;
        this.betaSdk = betaSdk;
    }

    private <T> @NotNull StreamObserver<T> createCallback(@NotNull CommandSender sender, @NotNull String sdkMethod,
                                                          @NotNull Function<T, String> nextMessageProvider) {
        return new StreamObserver<>() {

            @Override
            public void onNext(@NotNull T value) {
                String nextMessage = nextMessageProvider.apply(value);
                Component response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.NEXT, nextMessage);
                sender.sendMessage(response);
            }

            @Override
            public void onError(@NotNull Throwable exception) {
                Component response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.ERROR, exception.getMessage());
                sender.sendMessage(response);
            }

            @Override
            public void onCompleted() {
                Component response = generateMessage("SDK", sdkMethod, AgonesCommand.RequestStatus.COMPLETED, "");
                sender.sendMessage(response);
            }
        };
    }

    public void executeGetGameServer(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.getGameServer(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "GetGameServer", Object::toString));
    }

    public void executeListCounters(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.getGameServer(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "GetGameServer [Counters]", gameServer -> {
            Map<String, AgonesSDKProto.GameServer.Status.CounterStatus> counters = gameServer.getStatus().getCountersMap();
            StringJoiner joiner = new StringJoiner(", ", "Counters: \n", "");
            for (Map.Entry<String, AgonesSDKProto.GameServer.Status.CounterStatus> entry : counters.entrySet()) {
                String key = entry.getKey();
                AgonesSDKProto.GameServer.Status.CounterStatus value = entry.getValue();
                joiner.add("  - %s (%s/%s)".formatted(key, value.getCount(), value.getCapacity()));
            }

            return joiner.toString();
        }));
    }

    public void executeListLists(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.getGameServer(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "GetGameServer [Lists]", gameServer -> {
            Map<String, AgonesSDKProto.GameServer.Status.ListStatus> lists = gameServer.getStatus().getListsMap();

            StringJoiner joiner = new StringJoiner(", ", "Lists: \n", "");
            for (Map.Entry<String, AgonesSDKProto.GameServer.Status.ListStatus> entry : lists.entrySet()) {
                String key = entry.getKey();
                AgonesSDKProto.GameServer.Status.ListStatus value = entry.getValue();
                joiner.add("  - %s (%s/%s)".formatted(key, value.getValuesCount(), value.getCapacity()));
            }

            return joiner.toString();
        }));
    }

    public void executeGetList(@NotNull CommandSender sender, @NotNull CommandContext context) {
        String id = context.get("id");
        AgonesSDKProto.KeyValue keyValue = AgonesSDKProto.KeyValue.newBuilder().setKey(id).build();
        this.betaSdk.getList(BetaAgonesSDKProto.GetListRequest.newBuilder().setName(id).build(),
                this.createCallback(sender, "GetList", Object::toString));
    }

    public void executeSetAnnotation(@NotNull CommandSender sender, @NotNull CommandContext context) {
        String key = context.get("key");
        String value = context.get("value");

        AgonesSDKProto.KeyValue keyValue = AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build();
        this.sdk.setAnnotation(keyValue, this.createCallback(sender, "SetAnnotation", v -> "Set annotation %s to %s".formatted(key, v)));
    }

    public void executeSetLabel(@NotNull CommandSender sender, @NotNull CommandContext context) {
        String key = context.get("key");
        String value = context.get("value");

        AgonesSDKProto.KeyValue keyValue = AgonesSDKProto.KeyValue.newBuilder().setKey(key).setValue(value).build();
        this.sdk.setLabel(keyValue, this.createCallback(sender, "SetLabel", v -> "Set label %s to %s".formatted(key, v)));
    }

    public void executeShutdown(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.shutdown(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "Shutdown", v -> "Shutdown"));
    }

    public void executeWatchGameserver(@NotNull CommandSender sender, @NotNull CommandContext context) {
        this.sdk.watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), this.createCallback(sender, "WatchGameServer", Object::toString));
    }
}
