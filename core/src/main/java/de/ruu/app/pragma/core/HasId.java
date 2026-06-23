package de.ruu.app.pragma.core;

import org.jspecify.annotations.Nullable;

public interface HasId<ID>
{
    /**
     * The persistent identity of this object.
     *
     * <p><strong>IDs are generated exclusively by the JPA layer</strong> (via database sequence /
     * {@code @GeneratedValue}). All layers above JPA (DTO, Bean, FX) carry IDs read-only —
     * they copy the value from the layer below during mapping but never generate or assign new IDs.
     *
     * <p>May be {@code null} for objects that have not yet been persisted (new JPA entity before
     * {@code EntityManager.persist()}) or for DTOs/Beans populated from a request body that does
     * not include an ID.
     */
    @Nullable ID id();
}
