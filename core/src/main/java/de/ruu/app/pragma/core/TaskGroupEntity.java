package de.ruu.app.pragma.core;

import de.ruu.lib.jpa.core.Entity;

public interface TaskGroupEntity<T extends TaskEntity<?, ?>> extends TaskGroup<T>, Entity<Long>
{
}
