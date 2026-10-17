package edu.spaceship;

import edu.spaceship.proto.*;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ConcurrentHashMap;

/** The one place that manages gRPC player streams and delivers public/private messages. */
public final class Communication {
    private final ConcurrentHashMap<String, StreamObserver<ServerMessage>> clients = new ConcurrentHashMap<>();

    public void register(String id, StreamObserver<ServerMessage> observer) { clients.put(id, observer); }
    public void unregister(String id) { clients.remove(id); }
    public void sendPrivate(String id, String type, String text, String role, String phase, int integrity) {
        StreamObserver<ServerMessage> client = clients.get(id);
        if (client != null) safeSend(client, message(type, text, id, role, phase, integrity));
    }
    public void broadcast(String type, String text, String phase, int integrity) {
        clients.forEach((id, client) -> safeSend(client, message(type, text, id, "", phase, integrity)));
    }
    private void safeSend(StreamObserver<ServerMessage> client, ServerMessage message) {
        try { synchronized (client) { client.onNext(message); } } catch (RuntimeException ignored) { /* disconnected stream */ }
    }
    private ServerMessage message(String type, String text, String id, String role, String phase, int integrity) {
        return ServerMessage.newBuilder().setType(type).setText(text).setPlayerId(id).setRole(role).setPhase(phase).setShipIntegrity(integrity).build();
    }
}
