package de.ruu.app.pragma.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import de.ruu.app.pragma.bean.Mappings;
import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.bean.TaskGroupBean;
import de.ruu.app.pragma.dto.TaskDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.glassfish.jersey.client.ClientProperties;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * REST client for task operations.
 *
 * <p>The public interface works exclusively with {@link TaskBean} / {@link TaskGroupBean}.
 * DTO types are an internal transport detail — callers never need to import the {@code dto} module.
 *
 * <p>Methods that operate on persisted objects accept beans directly; the ID is extracted
 * internally.  Only the pure lookup methods {@link #findById} and {@link #findByIdWithRelated}
 * accept a raw {@code long id}, because those operations are inherently ID-driven.
 *
 * <p><strong>Tasks must always belong to a group.</strong> The {@code create(TaskBean)} method
 * enforces this: the bean's {@code taskGroup()} must be non-null and already persisted (id ≠ null).
 */
@Singleton
public class TaskClient
{
    private final String host = ConfigProvider.getConfig().getValue("pragma.rest-api.host", String.class);
    private final int    port = ConfigProvider.getConfig().getValue("pragma.rest-api.port", Integer.class);

    private Client client;

    @PostConstruct
    public void postConstruct()
    {
        ObjectMapper mapper = createObjectMapper();
        client = ClientBuilder.newBuilder()
            .register(new JacksonJsonProvider(mapper))
            .property(ClientProperties.CONNECT_TIMEOUT, 5000)
            .property(ClientProperties.READ_TIMEOUT,    15000)
            .build();
    }

    @PreDestroy
    public void preDestroy()
    {
        if (client != null) client.close();
    }

    // ── find all ──────────────────────────────────────────────────────────────

    public List<TaskBean> findAll()
    {
        try (Response response = target("/tasks").request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(new GenericType<List<TaskDto>>() {}));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public List<TaskBean> findAll(TaskGroupBean group)
    {
        try (Response response = target("/tasks").queryParam("groupId", id(group))
                .request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(new GenericType<List<TaskDto>>() {}));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    // ── find by id ────────────────────────────────────────────────────────────

    public Optional<TaskBean> findById(long id)
    {
        try (Response response = target("/tasks/" + id).request(MediaType.APPLICATION_JSON).get())
        {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) return Optional.empty();
            requireSuccess(response);
            return Optional.of(Mappings.toBean(response.readEntity(TaskDto.class)));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public Optional<TaskBean> findByIdWithRelated(long id)
    {
        try (Response response = target("/tasks/" + id + "/with-related")
                .request(MediaType.APPLICATION_JSON).get())
        {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) return Optional.empty();
            requireSuccess(response);
            return Optional.of(Mappings.toBean(response.readEntity(TaskDto.class)));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    // ── find with related ─────────────────────────────────────────────────────

    public List<TaskBean> findGroupTasksWithRelated(TaskGroupBean group)
    {
        try (Response response = target("/tasks/group/" + id(group) + "/with-related")
                .request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(new GenericType<List<TaskDto>>() {}));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    // ── create / update / delete ──────────────────────────────────────────────

    /**
     * Creates a task. The bean's {@code taskGroup()} must be non-null and persisted (id ≠ null)
     * since tasks must always belong to a group.
     */
    public TaskBean create(TaskBean bean)
    {
        long groupId = id(bean.taskGroup());
        var request  = new TaskCreateRequest(
            bean.name(), groupId,
            bean.description().orElse(null),
            bean.plannedStart().orElse(null),
            bean.plannedEnd()  .orElse(null),
            bean.closed()
        );
        try (Response response = target("/tasks")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request)))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public TaskBean update(TaskBean bean)
    {
        TaskDto dto = Mappings.toDto(bean);
        try (Response response = target("/tasks/" + id(bean))
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(dto)))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public void delete(TaskBean bean)
    {
        try (Response response = target("/tasks/" + id(bean)).request().delete())
        {
            requireSuccess(response);
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    // ── hierarchy ─────────────────────────────────────────────────────────────

    public TaskBean moveToGroup(TaskBean task, TaskGroupBean group)
    {
        try (Response response = target("/tasks/" + id(task) + "/group/" + id(group))
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json("")))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public TaskBean setParentTask(TaskBean task, TaskBean parent)
    {
        try (Response response = target("/tasks/" + id(task) + "/parent/" + id(parent))
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json("")))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public void clearParentTask(TaskBean task)
    {
        try (Response response = target("/tasks/" + id(task) + "/parent").request().delete())
        {
            requireSuccess(response);
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    // ── predecessors / successors ─────────────────────────────────────────────

    public TaskBean addPredecessor(TaskBean task, TaskBean predecessor)
    {
        try (Response response = target("/tasks/" + id(task) + "/predecessor/" + id(predecessor))
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json("")))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public void removePredecessor(TaskBean task, TaskBean predecessor)
    {
        try (Response response = target("/tasks/" + id(task) + "/predecessor/" + id(predecessor))
                .request().delete())
        {
            requireSuccess(response);
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public List<TaskBean> findPredecessors(TaskBean task)
    {
        try (Response response = target("/tasks/" + id(task) + "/predecessors")
                .request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(new GenericType<List<TaskDto>>() {}));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public List<TaskBean> findSuccessors(TaskBean task)
    {
        try (Response response = target("/tasks/" + id(task) + "/successors")
                .request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(new GenericType<List<TaskDto>>() {}));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private jakarta.ws.rs.client.WebTarget target(String path)
    {
        return client.target("http://" + host + ":" + port + "/pragma/api" + path);
    }

    private void requireSuccess(Response response)
    {
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL)
            throw new RuntimeException("HTTP " + response.getStatus() + " " + response.getStatusInfo().getReasonPhrase());
    }

    private long id(TaskBean bean)
    {
        Long id = bean.id();
        if (id == null) throw new IllegalArgumentException("TaskBean has no id — persist it first");
        return id;
    }

    private long id(TaskGroupBean bean)
    {
        Long id = bean.id();
        if (id == null) throw new IllegalArgumentException("TaskGroupBean has no id — persist it first");
        return id;
    }

    private ObjectMapper createObjectMapper()
    {
        return new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .setVisibility(VisibilityChecker.Std.defaultInstance()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private record TaskCreateRequest(
        String name, long groupId,
        @Nullable String description,
        @Nullable LocalDate plannedStart,
        @Nullable LocalDate plannedEnd,
        boolean closed
    ) {}
}
