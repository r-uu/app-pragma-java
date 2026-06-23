module de.ruu.app.pragma.dto
{
    requires de.ruu.app.pragma.core;
    requires org.jspecify;
    requires com.fasterxml.jackson.annotation;

    exports de.ruu.app.pragma.dto;
    opens   de.ruu.app.pragma.dto; // allow reflective access for JSON serialization (Jackson field visibility)
}
