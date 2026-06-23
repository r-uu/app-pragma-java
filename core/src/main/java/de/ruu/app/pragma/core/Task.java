package de.ruu.app.pragma.core;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Optional;

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
    Optional<String>    description();
    Optional<LocalDate> plannedStart();
    Optional<LocalDate> plannedEnd();
    Boolean             closed();

    T description (@Nullable String    description);
    T plannedStart(@Nullable LocalDate plannedStart);
    T plannedEnd  (@Nullable LocalDate plannedEnd);
    T closed      (          Boolean   closed);
}
