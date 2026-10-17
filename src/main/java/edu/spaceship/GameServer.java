package edu.spaceship;

import edu.spaceship.proto.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.UUID;

/** Starts the gRPC server and routes player streams to the game logic. */
public final class GameServer {
    private final GameState state = new GameState();
    private final GameLogic logic = new GameLogic(state);
    private final Communication communication = new Communication();

    private GameServer(int port) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(port).addService(new Service()).build().start();
        System.out.println("Spaceship Sabotage server listening on port " + port + ". Need " + GameState.MIN_PLAYERS + " players to start.");
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        server.awaitTermination();
    }

    private final class Service extends GameServiceGrpc.GameServiceImplBase {
        @Override public StreamObserver<ClientMessage> connect(StreamObserver<ServerMessage> response) {
            final String[] id = {UUID.randomUUID().toString().substring(0, 8)};
            final boolean[] registered = {false};
            return new StreamObserver<>() {
                @Override public void onNext(ClientMessage request) {
                    if (!registered[0]) {
                        String name = request.getPlayerName().isBlank() ? "Player" : request.getPlayerName();
                        String message = logic.register(id[0], name);
                        if (message.startsWith("The game")) { response.onNext(out("ERROR", message, id[0])); response.onCompleted(); return; }
                        registered[0] = true;
                        communication.register(id[0], response);
                        response.onNext(out("REGISTERED", message, id[0]));
                        state.getPlayers().values().stream().filter(p -> !p.getId().equals(id[0])).forEach(p -> communication.sendPrivate(id[0], "INFO", "Connected player: " + p.getName() + " (" + p.getId() + ")", "", state.getPhase().name(), state.getIntegrity()));
                        communication.broadcast("INFO", name + " joined (" + id[0] + "). " + state.getPlayers().size() + " player(s) connected.", state.getPhase().name(), state.getIntegrity());
                        logic.startIfReady().forEach((playerId, role) -> {
                            String teammates = role.equals("SABOTEUR") ? state.getPlayers().values().stream()
                                    .filter(p -> p.getRole() == PlayerState.Role.SABOTEUR && !p.getId().equals(playerId))
                                    .map(p -> p.getName() + " (" + p.getId() + ")").reduce((a, b) -> a + ", " + b).orElse("none") : "";
                            String roleText = "Your role is " + role + "." + (role.equals("SABOTEUR") ? " Your fellow saboteurs: " + teammates + "." : "");
                            communication.sendPrivate(playerId, "ROLE", roleText, role, state.getPhase().name(), state.getIntegrity());
                        });
                        if (state.getPhase() == GamePhase.OPERATE) communication.broadcast("INFO", "Game started! Use REPAIR or SABOTAGE. A discussion can be called with DISCUSS.", state.getPhase().name(), state.getIntegrity());
                        return;
                    }
                    GameLogic.Result result = logic.act(id[0], request.getAction(), request.getTarget(), request.getText());
                    if (request.getAction().equalsIgnoreCase("WHISPER") && result.privateMessage() && !result.message().startsWith("Unknown target")) {
                        communication.sendPrivate(request.getTarget(), "PRIVATE", result.message(), "", state.getPhase().name(), state.getIntegrity());
                        communication.sendPrivate(id[0], "PRIVATE", "Private message sent to " + request.getTarget() + ".", "", state.getPhase().name(), state.getIntegrity());
                    }
                    else if (result.privateMessage()) communication.sendPrivate(id[0], "PRIVATE", result.message(), "", state.getPhase().name(), state.getIntegrity());
                    else communication.broadcast("EVENT", result.message(), state.getPhase().name(), state.getIntegrity());
                    String voteResult = logic.takeVoteResult();
                    if (!voteResult.isBlank()) communication.broadcast("VOTE_RESULT", voteResult, state.getPhase().name(), state.getIntegrity());
                    if (state.getPhase() == GamePhase.FINISHED) communication.broadcast("GAME_OVER", state.getWinner() + " win!", state.getPhase().name(), state.getIntegrity());
                }
                @Override public void onError(Throwable t) { if (registered[0]) { communication.unregister(id[0]); } }
                @Override public void onCompleted() { if (registered[0]) communication.unregister(id[0]); response.onCompleted(); }
            };
        }
    }
    private static ServerMessage out(String type, String text, String id) { return ServerMessage.newBuilder().setType(type).setText(text).setPlayerId(id).build(); }
    public static void main(String[] args) throws Exception { int port = args.length > 0 ? Integer.parseInt(args[0]) : 50051; new GameServer(port); }
}
