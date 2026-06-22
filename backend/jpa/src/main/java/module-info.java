module de.ruu.app.pragma.jpa
{
    requires de.ruu.app.pragma.core;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires org.jspecify;
    // open to Hibernate for reflection-based entity mapping
    opens de.ruu.app.pragma.jpa;

    exports de.ruu.app.pragma.jpa;
}
