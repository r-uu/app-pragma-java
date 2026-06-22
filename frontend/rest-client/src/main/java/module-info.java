module de.ruu.app.pragma.client
{
    requires de.ruu.app.pragma.dto;
    requires de.ruu.app.pragma.bean;

    requires jakarta.activation;
    requires jakarta.annotation;
    requires jakarta.inject;
    requires jakarta.ws.rs;

    requires jersey.client;

    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.jakarta.rs.json;

    requires microprofile.config.api;
    requires org.jspecify;

    exports de.ruu.app.pragma.client;
    exports de.ruu.app.pragma.client.dbcommand;

    // open for CDI (Weld) bean discovery and proxy generation
    opens de.ruu.app.pragma.client;
    opens de.ruu.app.pragma.client.dbcommand;
}
