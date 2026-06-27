package de.ruu.app.pragma.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

@Liveness
@Readiness
@ApplicationScoped
public class PragmaHealthCheck implements HealthCheck
{
    @PersistenceContext
    private EntityManager em;

    @Override
    public HealthCheckResponse call()
    {
        try
        {
            em.createQuery("SELECT 1").getSingleResult();
            return HealthCheckResponse.up("pragma-database");
        }
        catch (Exception e)
        {
            return HealthCheckResponse.builder()
                .name("pragma-database")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
