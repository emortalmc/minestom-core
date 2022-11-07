package cc.towerdefence.minestom.module.kubernetes.command.agones.subs;//package cc.towerdefence.minestom.command.agones.subs;
//
//import cc.towerdefence.api.utils.utils.FunctionalFutureCallback;
//import cc.towerdefence.minestom.MinestomServer;
//import cc.towerdefence.minestom.command.agones.AgonesCommand;
//import com.google.common.util.concurrent.Futures;
//import com.google.common.util.concurrent.ListenableFuture;
//import dev.agones.sdk.alpha.AlphaAgonesSDKProto;
//import dev.agones.sdk.alpha.SDKGrpc;
//import io.grpc.stub.StreamObserver;
//import net.kyori.adventure.text.Component;
//import net.minestom.server.command.CommandSender;
//import net.minestom.server.command.builder.CommandContext;
//
//import java.util.UUID;
//import java.util.concurrent.ForkJoinPool;
//
//public class AlphaSubs {
//    private final SDKGrpc.SDKFutureStub sdk = MinestomServer.getAgonesManager().getAlphaSdk();
//
//    public void executeSetCapacity(CommandSender sender, CommandContext context) {
//        int capacity = context.get("value");
//        ListenableFuture<AlphaAgonesSDKProto.Empty> playerCapacityFuture = this.sdk.setPlayerCapacity(
//                AlphaAgonesSDKProto.Count.newBuilder().setCount(capacity).build()
//        );
//
//
//        Futures.addCallback(playerCapacityFuture, FunctionalFutureCallback.create(
//                success -> {
//                    Component response = AgonesCommand.generateMessage(
//                            "Alpha", "SetPlayerCapacity", AgonesCommand.RequestStatus.NEXT,
//                            "Set player capacity to " + capacity
//                    );
//
//                    sender.sendMessage(response);
//                },
//                throwable -> {
//                    Component response = AgonesCommand.generateMessage(
//                            "Alpha", "SetPlayerCapacity", AgonesCommand.RequestStatus.ERROR,
//                            throwable.getMessage()
//                    );
//
//                    sender.sendMessage(response);
//                }
//        ), ForkJoinPool.commonPool());
//    }
//
//    public void executeGetConnectedPlayers(CommandSender sender, CommandContext context) {
//
//        ListenableFuture<AlphaAgonesSDKProto.PlayerIDList> connectedPlayersFuture = this.sdk.getConnectedPlayers(AlphaAgonesSDKProto.Empty.getDefaultInstance());
//        Futures
//        , new StreamObserver<>() {
//            @Override
//            public void onNext(AlphaAgonesSDKProto.PlayerIDList value) {
//                Component response = AgonesCommand.generateMessage(
//                        "Alpha", "GetConnectedPlayers", AgonesCommand.RequestStatus.NEXT,
//                        "(%s): %s".formatted(value.getListCount(), value.getListList())
//                );
//
//                sender.sendMessage(response);
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                Component response = AgonesCommand.generateMessage(
//                        "Alpha", "GetConnectedPlayers", AgonesCommand.RequestStatus.ERROR,
//                        t.getMessage()
//                );
//
//                sender.sendMessage(response);
//            }
//        });
//    }
//
//    public void executeIsPlayerConnected(CommandSender sender, CommandContext context) {
//        UUID playerId = context.get("playerId");
//        this.sdk.isPlayerConnected(AlphaAgonesSDKProto.PlayerID.newBuilder().setPlayerID(playerId.toString()).build(), new StreamObserver<>() {
//            @Override
//            public void onNext(AlphaAgonesSDKProto.Bool value) {
//                Component response = AgonesCommand.generateMessage(
//                        "Alpha", "IsPlayerConnected", AgonesCommand.RequestStatus.NEXT,
//                        "Player %s is %sconnected".formatted(playerId, value.getBool() ? "" : "not ")
//                );
//
//                sender.sendMessage(response);
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                Component response = AgonesCommand.generateMessage(
//                        "Alpha", "IsPlayerConnected", AgonesCommand.RequestStatus.ERROR,
//                        t.getMessage()
//                );
//
//                sender.sendMessage(response);
//            }
//        });
//    }
//
//    public void executePlayerConnect(CommandSender sender, CommandContext context) {
//        UUID playerId = context.get("playerId");
//        this.sdk.playerConnect(AlphaAgonesSDKProto.PlayerID.newBuilder().setPlayerID(playerId.toString()).build(), new StreamObserver<>() {
//            @Override
//            public void onNext(AlphaAgonesSDKProto.Bool value) {
//                Component response = AgonesCommand.generateMessage(
//                        "Alpha", "PlayerConnect", AgonesCommand.RequestStatus.NEXT,
//                        value.getBool() ? "Player %s connected".formatted(playerId) : "Player %s already marked connected???? List unchanged".formatted(playerId)
//                );
//
//                sender.sendMessage(response);
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                Component response = AgonesCommand.generateMessage(
//                        "Alpha", "PlayerConnect", AgonesCommand.RequestStatus.ERROR,
//                        t.getMessage()
//                );
//
//                sender.sendMessage(response);
//            }
//        });
//    }
//
//    public void executePlayerDisconnect(CommandSender sender, CommandContext context) {
//        UUID playerId = context.get("playerId");
//        this.sdk.playerDisconnect(AlphaAgonesSDKProto.PlayerID.newBuilder().setPlayerID(playerId.toString()).build(), new StreamObserver<>() {
//            @Override
//            public void onNext(AlphaAgonesSDKProto.Bool value) {
//                Component response = AgonesCommand.generateMessage(
//                        "Alpha", "PlayerDisconnect", AgonesCommand.RequestStatus.NEXT,
//                        value.getBool() ? "Player %s marked disconnected".formatted(playerId) : "Player %s is not marked as connected??? List unchanged".formatted(playerId)
//                );
//
//                sender.sendMessage(response);
//            }
//
//            @Override
//            public void onError(Throwable t) {
//                Component response = AgonesCommand.generateMessage(
//                        "Alpha", "PlayerDisconnect", AgonesCommand.RequestStatus.ERROR,
//                        t.getMessage()
//                );
//
//                sender.sendMessage(response);
//            }
//        });
//    }
//}
