package de.ruu.app.pragma.rest;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException>
{
    @Override
    public Response toResponse(ConstraintViolationException e)
    {
        var violations = e.getConstraintViolations().stream()
            .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
            .sorted()
            .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",     400);
        body.put("title",      "Validation Failed");
        body.put("violations", violations);

        return Response.status(Response.Status.BAD_REQUEST)
                       .entity(body)
                       .type(MediaType.APPLICATION_JSON)
                       .build();
    }
}
