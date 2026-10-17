# Spaceship Sabotage — Java + gRPC

A text-based multiplayer social deduction game. The server owns synchronized game state, and each player runs as a separate Java process connected over a bidirectional gRPC stream. The original game logic and `spaceship.proto` API are retained. Maven generates the protobuf messages and gRPC service classes during the build; generated files are intentionally not checked in.

## Requirements

- JDK 17 or newer
- Maven 3.8 or newer
- Internet access for the first Maven build so it can download dependencies and native protobuf compiler tools

The same project commands work on macOS and Linux, including Apple Silicon Macs. `os-maven-plugin` detects the platform classifier used to fetch `protoc` and `protoc-gen-grpc-java`.

## Build

Run from the directory containing `pom.xml`:

```sh
mvn clean package
```

The protobuf messages and gRPC service implementation are generated under `target/generated-sources/` and compiled with the application. If those classes appear missing in an IDE, reload the Maven project and run the build above; do not create or edit the generated files by hand.

## Run

Open one terminal for the server:

```sh
mvn exec:java -Dexec.mainClass=edu.spaceship.GameServer -Dexec.args=50051
```

Open at least four additional terminals and start one player in each:

```sh
mvn exec:java -Dexec.mainClass=edu.spaceship.Player -Dexec.args="localhost 50051 Alice"
```

Change `Alice` to a different player name for each process. `GameServer` defaults to port `50051` when no port is supplied; `Player` defaults to `localhost:50051` and prompts for a name when one is omitted. Use `Ctrl+C` to stop the server.

On Linux, if you want each player to use a distinct OS account, first build the project and make it readable by those accounts, then launch a player command as each account, for example:

```sh
sudo -u player1 -- mvn -f /path/to/project/pom.xml exec:java -Dexec.mainClass=edu.spaceship.Player -Dexec.args="localhost 50051 Alice"
```

Each invocation is a separate process. Account creation and permissions are managed by the Linux system administrator; the game itself does not create OS users.

## Player commands

- `STATUS` — show game phase, ship integrity, and living role counts.
- `REPAIR` — Crew repairs the ship; three repairs win for Crew.
- `SABOTAGE [system]` — a Saboteur damages ship integrity; zero integrity wins for Saboteurs.
- `DISCUSS` — open a discussion and voting round.
- `CHAT <message>` — send a public message during discussion.
- `WHISPER <player-id> <message>` — send a private message. Player IDs are shown when players join.
- `VOTE <player-id>` — vote to eject a living player; all living players must vote.
- `VOTE SKIP` — abstain; a tie or all-skip means no ejection.
- `QUIT` — disconnect the player.

Crew also win when every Saboteur has been ejected. Saboteurs win when no Crew remain.

## Project layout

```text
pom.xml
src/main/proto/spaceship.proto
src/main/java/edu/spaceship/
```

`Communication.java` handles stream registration and message delivery; `GameLogic.java` validates actions and synchronizes updates to `GameState`.
