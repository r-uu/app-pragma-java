package de.ruu.app.pragma.core;

import java.util.Optional;

public interface HasParentTask<T>
{
    Optional<T> parentTask();
}
