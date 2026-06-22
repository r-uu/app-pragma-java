package de.ruu.app.pragma.core;

import de.ruu.lib.jpa.core.Entity;

public interface TaskEntity
        <G extends TaskGroupEntity<? extends TaskEntity<G, ?>>, T extends TaskEntity<G, T>>
        extends Task<G, T>, Entity<Long>
{
}
