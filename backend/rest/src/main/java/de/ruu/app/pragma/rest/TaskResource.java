package de.ruu.app.pragma.rest;

import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.jpa.TaskGroupJPA;
import de.ruu.app.pragma.jpa.TaskJPA;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Path("/tasks")
@RequestScoped
@Transactional
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource
{
    @PersistenceContext
    private EntityManager em;

    @GET
    public List<TaskDto> findAll(
            @QueryParam("groupId") Long    groupId,
            @QueryParam("page")    Integer page,
            @QueryParam("size")    Integer size)
    {
        int offset = page != null && page > 0 ? page * effectiveSize(size) : 0;
        if (groupId != null) {
            return em.createQuery("SELECT t FROM TaskJPA t WHERE t.taskGroup.id = :gid", TaskJPA.class)
                     .setParameter("gid", groupId)
                     .setFirstResult(offset)
                     .setMaxResults(effectiveSize(size))
                     .getResultList()
                     .stream()
                     .map(Mappings::toDto)
                     .toList();
        }
        return em.createQuery("SELECT t FROM TaskJPA t", TaskJPA.class)
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
    public TaskDto findById(@PathParam("id") Long id)
    {
        return Mappings.toDto(requireTask(id));
    }

    @GET
    @Path("/{id}/with-related")
    public TaskDto findByIdWithRelated(@PathParam("id") Long id)
    {
        EntityGraph<TaskJPA> graph = em.createEntityGraph(TaskJPA.class);
        graph.addAttributeNodes("subTasks", "predecessors", "successors");
        TaskJPA entity = em.find(TaskJPA.class, id, Map.of("jakarta.persistence.fetchgraph", graph));
        if (entity == null) throw new NotFoundException("Task not found: " + id);
        return Mappings.toDto(entity);
    }

    @GET
    @Path("/group/{groupId}/with-related")
    public List<TaskDto> findGroupTasksWithRelated(@PathParam("groupId") Long groupId)
    {
        EntityGraph<TaskJPA> graph = em.createEntityGraph(TaskJPA.class);
        graph.addAttributeNodes("subTasks", "predecessors", "successors");
        List<TaskJPA> tasks = em.createQuery(
                "SELECT DISTINCT t FROM TaskJPA t WHERE t.taskGroup.id = :gid", TaskJPA.class)
            .setParameter("gid", groupId)
            .setHint("jakarta.persistence.fetchgraph", graph)
            .getResultList();
        return Mappings.toDto(tasks);
    }

    @POST
    public Response create(@Valid TaskCreateRequest request)
    {
        if (request.groupId() == null) throw new BadRequestException("groupId is required");
        TaskGroupJPA group = em.find(TaskGroupJPA.class, request.groupId());
        if (group == null) throw new NotFoundException("TaskGroup not found: " + request.groupId());
        TaskJPA entity = new TaskJPA(request.name(), group);
        entity.description (request.description());
        entity.plannedStart(request.plannedStart());
        entity.plannedEnd  (request.plannedEnd());
        entity.closed      (request.closed());
        em.persist(entity);
        return Response.status(Response.Status.CREATED).entity(Mappings.toDto(entity)).build();
    }

    @PUT
    @Path("/{id}")
    public TaskDto update(@PathParam("id") Long id, @Valid TaskDto dto)
    {
        TaskJPA entity = requireTask(id);
        if (!Objects.equals(entity.version(), dto.version()))
            throw new WebApplicationException(Response.Status.CONFLICT);
        entity.name       (dto.name());
        entity.description(dto.description().orElse(null));
        entity.plannedStart(dto.plannedStart().orElse(null));
        entity.plannedEnd  (dto.plannedEnd()  .orElse(null));
        entity.closed      (dto.closed());
        return Mappings.toDto(entity);
    }

    @PUT
    @Path("/{id}/group/{groupId}")
    public TaskDto moveToGroup(@PathParam("id") Long id, @PathParam("groupId") Long groupId)
    {
        TaskJPA entity = requireTask(id);
        TaskGroupJPA group = em.find(TaskGroupJPA.class, groupId);
        if (group == null) throw new NotFoundException("TaskGroup not found: " + groupId);
        entity.taskGroup(group);
        return Mappings.toDto(entity);
    }

    @PUT
    @Path("/{id}/parent/{parentId}")
    public TaskDto setParentTask(@PathParam("id") Long id, @PathParam("parentId") Long parentId)
    {
        TaskJPA task   = requireTask(id);
        TaskJPA parent = requireTask(parentId);
        task.parentTask().ifPresent(old -> old.removeSubTask(task));
        parent.addSubTask(task);
        return Mappings.toDto(task);
    }

    @DELETE
    @Path("/{id}/parent")
    public Response clearParentTask(@PathParam("id") Long id)
    {
        TaskJPA task = requireTask(id);
        task.parentTask().ifPresent(parent -> parent.removeSubTask(task));
        return Response.noContent().build();
    }

    @PUT
    @Path("/{id}/predecessor/{predId}")
    public TaskDto addPredecessor(@PathParam("id") Long id, @PathParam("predId") Long predId)
    {
        TaskJPA task = requireTask(id);
        TaskJPA pred = requireTask(predId);
        task.addPredecessor(pred);
        return Mappings.toDto(task);
    }

    @DELETE
    @Path("/{id}/predecessor/{predId}")
    public Response removePredecessor(@PathParam("id") Long id, @PathParam("predId") Long predId)
    {
        TaskJPA task = requireTask(id);
        TaskJPA pred = requireTask(predId);
        task.removePredecessor(pred);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/predecessors")
    public List<TaskDto> findPredecessors(@PathParam("id") Long id)
    {
        TaskJPA task = requireTask(id);
        return task.predecessors()
                   .map(set -> set.stream().map(Mappings::toDto).toList())
                   .orElse(List.of());
    }

    @GET
    @Path("/{id}/successors")
    public List<TaskDto> findSuccessors(@PathParam("id") Long id)
    {
        TaskJPA task = requireTask(id);
        return task.successors()
                   .map(set -> set.stream().map(Mappings::toDto).toList())
                   .orElse(List.of());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id)
    {
        TaskJPA entity = requireTask(id);
        entity.taskGroup().removeTask(entity);
        em.remove(entity);
        return Response.noContent().build();
    }

    private TaskJPA requireTask(Long id)
    {
        TaskJPA entity = em.find(TaskJPA.class, id);
        if (entity == null) throw new NotFoundException("Task not found: " + id);
        return entity;
    }

    public record TaskCreateRequest(
        @NotBlank String name,
        @NotNull  Long groupId,
        @Nullable String description,
        @Nullable LocalDate plannedStart,
        @Nullable LocalDate plannedEnd,
        boolean closed
    ) {}
}
