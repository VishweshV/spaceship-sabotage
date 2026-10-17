package edu.spaceship;

import java.util.*;

/** Validates player actions and updates the synchronized shared state. */
public final class GameLogic {
    public record Result(String message, boolean privateMessage) {}
    private final GameState state;
    private final Random random = new Random();

    public GameLogic(GameState state) { this.state = state; }

    public synchronized String register(String id, String name) {
        if (state.getPhase() != GamePhase.LOBBY) return "The game has already started.";
        if (name.isBlank()) return "Player name cannot be empty.";
        state.getPlayers().put(id, new PlayerState(id, name.trim()));
        return "Registered as " + name.trim() + " (" + id + "). Waiting for " + GameState.MIN_PLAYERS + " players.";
    }

    public synchronized Map<String, String> startIfReady() {
        if (state.getPhase() != GamePhase.LOBBY || state.getPlayers().size() < GameState.MIN_PLAYERS) return Map.of();
        List<PlayerState> players = new ArrayList<>(state.getPlayers().values());
        Collections.shuffle(players, random);
        int saboteurCount = Math.max(1, players.size() / 4);
        for (int i = 0; i < players.size(); i++) players.get(i).setRole(i < saboteurCount ? PlayerState.Role.SABOTEUR : PlayerState.Role.CREW);
        state.setPhase(GamePhase.OPERATE);
        Map<String, String> roles = new LinkedHashMap<>();
        players.forEach(p -> roles.put(p.getId(), p.getRole().name()));
        return roles;
    }

    public synchronized Result act(String id, String action, String target, String text) {
        PlayerState player = state.getPlayers().get(id);
        if (player == null) return new Result("Register before acting.", true);
        if (!player.isAlive()) return new Result("Eliminated players cannot act.", true);
        action = action.toUpperCase(Locale.ROOT);
        switch (action) {
            case "STATUS" -> { return new Result("Phase: " + state.getPhase() + " | Integrity: " + state.getIntegrity() + "% | Living crew: " + state.aliveCount(PlayerState.Role.CREW) + " | Living saboteurs: " + state.aliveCount(PlayerState.Role.SABOTEUR), true); }
            case "REPAIR", "TASK" -> {
                if (state.getPhase() != GamePhase.OPERATE) return new Result("Repairs are only allowed during OPERATE.", true);
                if (player.getRole() != PlayerState.Role.CREW) return new Result("Saboteurs cannot complete crew repairs.", true);
                player.completeTask(); state.setIntegrity(state.getIntegrity() + 10);
                if (state.completedTasks() >= GameState.TASKS_TO_WIN) finish("CREW");
                return new Result(player.getName() + " completed a repair. Ship integrity is " + state.getIntegrity() + "% (" + state.completedTasks() + "/" + GameState.TASKS_TO_WIN + " repairs).", false);
            }
            case "SABOTAGE" -> {
                if (state.getPhase() != GamePhase.OPERATE) return new Result("Sabotage is only allowed during OPERATE.", true);
                if (player.getRole() != PlayerState.Role.SABOTEUR) return new Result("Only saboteurs can sabotage.", true);
                state.setIntegrity(state.getIntegrity() - 20);
                checkWin();
                return new Result("A system was sabotaged. Ship integrity is now " + state.getIntegrity() + "%.", false);
            }
            case "CHAT", "SAY" -> {
                if (state.getPhase() != GamePhase.DISCUSSION) return new Result("Public discussion is only allowed during DISCUSSION.", true);
                return new Result(player.getName() + ": " + (text.isBlank() ? target : text), false);
            }
            case "WHISPER" -> {
                PlayerState recipient = state.getPlayers().get(target);
                if (recipient == null) return new Result("Unknown target. Use the player's ID.", true);
                return new Result("[Private from " + player.getName() + "] " + text, true);
            }
            case "DISCUSS" -> {
                if (state.getPhase() == GamePhase.LOBBY) return new Result("The game has not started.", true);
                state.setPhase(GamePhase.DISCUSSION);
                state.getVotes().clear();
                return new Result("Emergency discussion called by " + player.getName() + ". Vote phase is now open.", false);
            }
            case "VOTE" -> {
                if (state.getPhase() != GamePhase.DISCUSSION && state.getPhase() != GamePhase.VOTING) return new Result("Voting is not open. Call DISCUSS first.", true);
                boolean skip = target.equalsIgnoreCase("SKIP");
                PlayerState candidate = skip ? null : state.getPlayers().get(target);
                if (!skip && (candidate == null || !candidate.isAlive())) return new Result("Choose the ID of a living player, or use SKIP.", true);
                state.setPhase(GamePhase.VOTING);
                state.getVotes().put(id, skip ? "SKIP" : target);
                if (state.allLivingPlayersVoted()) resolveVote();
                return new Result("Vote recorded. " + state.getVotes().size() + " vote(s) received.", false);
            }
            default -> { return new Result("Unknown action. Try STATUS, REPAIR, SABOTAGE, DISCUSS, CHAT, WHISPER, or VOTE.", true); }
        }
    }

    private void resolveVote() {
        Map<String, Long> counts = new HashMap<>();
        state.getVotes().values().stream().filter(v -> !v.equals("SKIP")).forEach(v -> counts.merge(v, 1L, Long::sum));
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<String> leaders = counts.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();
        String result;
        if (max == 0 || leaders.size() != 1) result = "Voting ended in a tie or skip; no one was ejected.";
        else {
            PlayerState eliminated = state.getPlayers().get(leaders.get(0));
            eliminated.setAlive(false);
            result = eliminated.getName() + " was ejected. Their role was " + eliminated.getRole() + ".";
        }
        state.getVotes().clear();
        if (state.getPhase() != GamePhase.FINISHED) state.setPhase(GamePhase.OPERATE);
        checkWin();
        state.setWinner(state.getWinner().isEmpty() ? "" : state.getWinner());
        lastVoteResult = result;
    }
    private String lastVoteResult = "";
    public synchronized String takeVoteResult() { String result = lastVoteResult; lastVoteResult = ""; return result; }
    private void checkWin() {
        if (state.getIntegrity() <= 0 || state.aliveCount(PlayerState.Role.CREW) == 0) finish("SABOTEURS");
        else if (state.aliveCount(PlayerState.Role.SABOTEUR) == 0) finish("CREW");
    }
    private void finish(String winner) { state.setWinner(winner); state.setPhase(GamePhase.FINISHED); }
    public synchronized GameState getState() { return state; }
}
