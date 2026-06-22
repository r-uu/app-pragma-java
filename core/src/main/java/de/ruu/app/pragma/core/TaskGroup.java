package de.ruu.app.pragma.core;

import java.util.Optional;
import java.util.Set;

public interface TaskGroup<T extends Task<?, ?>> extends RawTaskGroup, HasMutableName
{
    /** Empty Optional: not yet loaded (lazy default). Present Optional: loaded (set may be empty). */
    Optional<Set<T>> tasks();

    /**
     * Moves task into this group. Controlling method for the task-group relationship.
     * Sets task.taskGroup to this (owning side) and maintains both collections.
     * If task belongs to another group, it is removed from that group's collection first.
     * Recursion is prevented by Set.add() returning false when task is already present.
     */
    void addTask(T task);

    /**
     * Removes task from this group's tasks collection (inverse side only).
     * Does NOT clear task.taskGroup — tasks must always belong to a group.
     * Use only during task deletion. To move a task, call addTask() on the target group.
     */
    void removeTask(T task);
}
