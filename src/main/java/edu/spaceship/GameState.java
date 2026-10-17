package edu.spaceship;

import java.util.*;

/** All mutable shared state. GameLogic synchronizes every state-changing operation on this object. */
public final class GameState {
    public static final int MIN_PLAYERS = 4;
    public static final int STARTING_INTEGRITY = 100;
    public static final int TASKS_TO_WIN = 3;
    private final Map<String, PlayerState> players = new LinkedHashMap<>();
    private final Map<String, String> votes = new HashMap<>();
    private int integrity = STARTING_INTEGRITY;
    private GamePhase phase = GamePhase.LOBBY;
    private String winner = "";

    public Map<String, PlayerState> getPlayers() { return players; }
    public Map<String, String> getVotes() { return votes; }
    public int getIntegrity() { return integrity; }
    public void setIntegrity(int integrity) { this.integrity = Math.max(0, Math.min(STARTING_INTEGRITY, integrity)); }
    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public long aliveCount(PlayerState.Role role) { return players.values().stream().filter(p -> p.isAlive() && p.getRole() == role).count(); }
    public int completedTasks() { return players.values().stream().mapToInt(PlayerState::getTasksCompleted).sum(); }
    public boolean allLivingPlayersVoted() { return players.values().stream().filter(PlayerState::isAlive).allMatch(p -> votes.containsKey(p.getId())); }
}
