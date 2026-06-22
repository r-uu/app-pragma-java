package de.ruu.app.pragma.core;

public interface HasMutablePredecessors<T> extends HasPredecessors<T>
{
    /** Adds predecessor and registers this as successor on predecessor. Bidirectional. */
    void addPredecessor(T predecessor);

    /** Removes predecessor and deregisters this as successor on predecessor. Bidirectional. */
    void removePredecessor(T predecessor);
}
