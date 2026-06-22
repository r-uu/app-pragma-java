package de.ruu.app.pragma.core;

public interface HasMutableTaskGroup<G extends TaskGroup<?>> extends HasTaskGroup<G>
{
    /**
     * Moves this task to the given group by delegating to group.addTask().
     * Null is not permitted — tasks must always belong to a group.
     */
    HasMutableTaskGroup<G> taskGroup(G taskGroup);
}
