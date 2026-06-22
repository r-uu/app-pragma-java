package de.ruu.app.pragma.core;

import java.util.Optional;
import java.util.Set;

public interface HasSubTasks<T>
{
    /** Empty Optional: not yet loaded (lazy default). Present Optional: loaded (set may be empty). */
    Optional<Set<T>> subTasks();
}
