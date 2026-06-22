package de.ruu.app.pragma.core;

import org.jspecify.annotations.Nullable;

public interface HasId<ID>
{
    /** May be null before persistence (e.g. unsaved JPA entity or unpopulated DTO). */
    @Nullable ID id();
}
