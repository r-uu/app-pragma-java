package de.ruu.app.pragma.core;

public interface HasMutableSubTasks<T> extends HasSubTasks<T>
{
    /** Adds subTask and sets subTask.parentTask to this. Bidirectional. */
    void addSubTask(T subTask);

    /** Removes subTask and clears subTask.parentTask if it points to this. Bidirectional. */
    void removeSubTask(T subTask);
}
