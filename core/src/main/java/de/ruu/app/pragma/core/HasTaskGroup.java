package de.ruu.app.pragma.core;

import java.util.Optional;

public interface HasTaskGroup<G extends TaskGroup<?>>
{
    Optional<G> taskGroup();
}
