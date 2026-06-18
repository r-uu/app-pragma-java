package de.ruu.app.pragma.core;

public interface HasParentTask<T>
{
    T    getParentTask();
    void setParentTask(T parentTask);
}
