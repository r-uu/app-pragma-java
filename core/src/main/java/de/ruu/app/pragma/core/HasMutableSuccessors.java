package de.ruu.app.pragma.core;

public interface HasMutableSuccessors<T> extends HasSuccessors<T>
{
    /** Adds successor and registers this as predecessor on successor. Bidirectional. */
    void addSuccessor(T successor);

    /** Removes successor and deregisters this as predecessor on successor. Bidirectional. */
    void removeSuccessor(T successor);
}
