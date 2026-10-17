package edu.spaceship;

import edu.spaceship.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/** One interactive player client. Start one JVM process per player. */
public final class Player {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 50051;
        String name = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
        Scanner scanner = new Scanner(System.in);
        if (name.isBlank()) { System.out.print("Player name: "); name = scanner.nextLine().trim(); }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        CountDownLatch finished = new CountDownLatch(1);
        StreamObserver<ServerMessage> incoming = new StreamObserver<>() {
            @Override public void onNext(ServerMessage msg) { System.out.println("\n[" + msg.getType() + "] " + msg.getText() + (msg.getShipIntegrity() > 0 ? " | integrity " + msg.getShipIntegrity() + "%" : "")); System.out.print("> "); }
            @Override public void onError(Throwable t) { System.err.println("Disconnected: " + t.getMessage()); finished.countDown(); }
            @Override public void onCompleted() { System.out.println("Server closed the connection."); finished.countDown(); }
        };
        StreamObserver<ClientMessage> outgoing = GameServiceGrpc.newStub(channel).connect(incoming);
        outgoing.onNext(ClientMessage.newBuilder().setPlayerName(name).build());
        System.out.println("Commands: STATUS | REPAIR | SABOTAGE [system] | DISCUSS | CHAT <message> | WHISPER <player-id> <message> | VOTE <player-id> | VOTE SKIP | QUIT");
        while (!finished.await(1, java.util.concurrent.TimeUnit.MILLISECONDS) && scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("QUIT")) break;
            if (line.isBlank()) continue;
            String[] parts = line.split("\\s+", 3);
            String action = parts[0].toUpperCase();
            String target = parts.length > 1 ? parts[1] : "";
            String text = parts.length > 2 ? parts[2] : "";
            if (action.equals("CHAT")) { text = line.length() > 5 ? line.substring(5).trim() : ""; target = ""; }
            if (action.equals("WHISPER") && parts.length > 2) text = parts[2];
            outgoing.onNext(ClientMessage.newBuilder().setAction(action).setTarget(target).setText(text).build());
        }
        outgoing.onCompleted(); channel.shutdownNow();
    }
}
