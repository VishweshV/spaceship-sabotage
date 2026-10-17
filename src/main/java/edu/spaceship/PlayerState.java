package edu.spaceship;

/** Server-side information for one connected player. */
public final class PlayerState {
    public enum Role { CREW, SABOTEUR }
    private final String id;
    private final String name;
    private Role role;
    private boolean alive = true;
    private int tasksCompleted;

    public PlayerState(String id, String name) { this.id = id; this.name = name; }
    public String getId() { return id; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public int getTasksCompleted() { return tasksCompleted; }
    public void completeTask() { tasksCompleted++; }
}
