package de.ruu.app.pragma.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception>
{
    private static final Logger log = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception e)
    {
        // WebApplicationException already carries the correct HTTP status — pass through.
        if (e instanceof WebApplicationException wae) return wae.getResponse();

        log.log(Level.SEVERE, "unhandled exception", e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 500);
        body.put("title",  "Internal Server Error");
        body.put("detail", e.getMessage());

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                       .entity(body)
                       .type(MediaType.APPLICATION_JSON)
                       .build();
    }
}
