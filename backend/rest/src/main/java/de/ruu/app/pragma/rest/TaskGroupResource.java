package de.ruu.app.pragma.rest;

import de.ruu.app.pragma.dto.TaskGroupDto;
import de.ruu.app.pragma.jpa.TaskGroupJPA;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.validation.Valid;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Objects;

@Path("/task-groups")
@RequestScoped
@Transactional
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskGroupResource
{
    @PersistenceContext
    private EntityManager em;

    @GET
    public List<TaskGroupDto> findAll(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size)
    {
        int offset = page != null && page > 0 ? page * effectiveSize(size) : 0;
        return em.createQuery("SELECT g FROM TaskGroupJPA g", TaskGroupJPA.class)
                 .setFirstResult(offset)
                 .setMaxResults(effectiveSize(size))
                 .getResultList()
                 .stream()
                 .map(Mappings::toDto)
                 .toList();
    }

    private static int effectiveSize(Integer size) { return (size != null && size > 0) ? size : Integer.MAX_VALUE; }

    @GET
    @Path("/{id}")
    public TaskGroupDto findById(@PathParam("id") Long id)
    {
        return Mappings.toDto(requireGroup(id));
    }

    @POST
    public Response create(@Valid TaskGroupDto dto)
    {
        TaskGroupJPA entity = new TaskGroupJPA(dto.name());
        em.persist(entity);
        return Response.status(Response.Status.CREATED).entity(Mappings.toDto(entity)).build();
    }

    @PUT
    @Path("/{id}")
    public TaskGroupDto update(@PathParam("id") Long id, @Valid TaskGroupDto dto)
    {
        TaskGroupJPA entity = requireGroup(id);
        if (!Objects.equals(entity.version(), dto.version()))
            throw new WebApplicationException(Response.Status.CONFLICT);
        entity.name(dto.name());
        return Mappings.toDto(entity);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id)
    {
        em.remove(requireGroup(id));
        return Response.noContent().build();
    }

    private TaskGroupJPA requireGroup(Long id)
    {
        TaskGroupJPA entity = em.find(TaskGroupJPA.class, id);
        if (entity == null) throw new NotFoundException("TaskGroup not found: " + id);
        return entity;
    }
}
