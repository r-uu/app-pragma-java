package de.ruu.app.pragma.core;

import org.jspecify.annotations.Nullable;

public interface HasMutableParentTask<T> extends HasParentTask<T>
{
    /** Null is permitted: passing null clears the parent, making this task a root task. */
    T parentTask(@Nullable T parentTask);
}
