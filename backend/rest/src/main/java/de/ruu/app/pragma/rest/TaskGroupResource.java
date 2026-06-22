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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

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
    public List<TaskGroupDto> findAll()
    {
        return em.createQuery("SELECT g FROM TaskGroupJPA g", TaskGroupJPA.class)
                 .getResultList()
                 .stream()
                 .map(Mappings::toDto)
                 .toList();
    }

    @GET
    @Path("/{id}")
    public TaskGroupDto findById(@PathParam("id") Long id)
    {
        return Mappings.toDto(requireGroup(id));
    }

    @POST
    public Response create(TaskGroupDto dto)
    {
        TaskGroupJPA entity = new TaskGroupJPA(dto.name());
        em.persist(entity);
        return Response.status(Response.Status.CREATED).entity(Mappings.toDto(entity)).build();
    }

    @PUT
    @Path("/{id}")
    public TaskGroupDto update(@PathParam("id") Long id, TaskGroupDto dto)
    {
        TaskGroupJPA entity = requireGroup(id);
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

    /** Deletes all task groups and their tasks (including ManyToMany join table). */
    @DELETE
    public Response deleteAll()
    {
        em.createNativeQuery("DELETE FROM task_predecessor").executeUpdate();
        em.createQuery("DELETE FROM TaskJPA").executeUpdate();
        em.createQuery("DELETE FROM TaskGroupJPA").executeUpdate();
        return Response.noContent().build();
    }

    private TaskGroupJPA requireGroup(Long id)
    {
        TaskGroupJPA entity = em.find(TaskGroupJPA.class, id);
        if (entity == null) throw new NotFoundException("TaskGroup not found: " + id);
        return entity;
    }
}
