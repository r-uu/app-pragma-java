package de.ruu.app.pragma.core;

public interface HasTaskGroup<G extends TaskGroup<?>>
{
    /**
     * Returns the task group this task belongs to.
     *
     * <p><strong>Tasks must always belong to a group.</strong> A task without a group is invalid
     * and must never exist — this invariant holds across all layers (JPA, DTO, Bean, FX, Client).
     *
     * <p>The only exception is a brief transient window during object construction, between the
     * no-arg constructor call and the first {@code taskGroupInternal()} call inside
     * {@link de.ruu.app.pragma.core.TaskGroup#addTask}. In this window the return value may be
     * {@code null}. Callers in {@code addTask()} must null-check accordingly; all other callers
     * may assume non-null.
     */
    G taskGroup();
}
