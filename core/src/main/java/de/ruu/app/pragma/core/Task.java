package de.ruu.app.pragma.core;

public interface Task<T extends Task<T>>
        extends HasId<Long>, HasTitle, HasParentTask<T>, HasSubTasks<T>
{
}
