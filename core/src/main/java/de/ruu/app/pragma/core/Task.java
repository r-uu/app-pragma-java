package de.ruu.app.pragma.core;

public interface Task<G extends TaskGroup<? extends Task<G, ?>>, T extends Task<G, T>>
        extends
            RawTask,
            HasMutableName,
            HasMutableParentTask<T>,
            HasMutableSubTasks<T>,
            HasMutablePredecessors<T>,
            HasMutableSuccessors<T>,
            HasMutableTaskGroup<G>
{
}
