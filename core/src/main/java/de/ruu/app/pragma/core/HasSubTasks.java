package de.ruu.app.pragma.core;

import java.util.List;

public interface HasSubTasks<T>
{
    List<T> getSubTasks();
}
