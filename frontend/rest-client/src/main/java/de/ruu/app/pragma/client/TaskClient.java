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
 * <p>The public interface works exclusively with {@link TaskBean} / {@link de.ruu.app.pragma.bean.TaskGroupBean}.
 * DTO types are an internal transport detail — callers never need to import the {@code dto} module.
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

    public List<TaskBean> findAll(@Nullable Long groupId)
    {
        var target = target("/tasks");
        if (groupId != null) target = target.queryParam("groupId", groupId);
        try (Response response = target.request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            List<TaskDto> dtos = response.readEntity(new GenericType<List<TaskDto>>() {});
            return Mappings.toBean(dtos);
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

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

    public List<TaskBean> findGroupTasksWithRelated(long groupId)
    {
        try (Response response = target("/tasks/group/" + groupId + "/with-related")
                .request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(new GenericType<List<TaskDto>>() {}));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    /**
     * Creates a task. The bean's {@code taskGroup()} must be non-null and persisted (id ≠ null)
     * since tasks must always belong to a group.
     */
    public TaskBean create(TaskBean bean)
    {
        Long groupId = bean.taskGroup().id();
        if (groupId == null) throw new IllegalArgumentException("bean.taskGroup().id() is null — persist the group first");
        var request = new TaskCreateRequest(
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

    public TaskBean update(long id, TaskBean bean)
    {
        TaskDto dto = Mappings.toDto(bean);
        try (Response response = target("/tasks/" + id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(dto)))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public TaskBean moveToGroup(long taskId, long groupId)
    {
        try (Response response = target("/tasks/" + taskId + "/group/" + groupId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json("")))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public TaskBean setParentTask(long taskId, long parentId)
    {
        try (Response response = target("/tasks/" + taskId + "/parent/" + parentId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json("")))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public void clearParentTask(long taskId)
    {
        try (Response response = target("/tasks/" + taskId + "/parent").request().delete())
        {
            requireSuccess(response);
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public TaskBean addPredecessor(long taskId, long predId)
    {
        try (Response response = target("/tasks/" + taskId + "/predecessor/" + predId)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json("")))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public void removePredecessor(long taskId, long predId)
    {
        try (Response response = target("/tasks/" + taskId + "/predecessor/" + predId).request().delete())
        {
            requireSuccess(response);
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public List<TaskBean> findPredecessors(long taskId)
    {
        try (Response response = target("/tasks/" + taskId + "/predecessors")
                .request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(new GenericType<List<TaskDto>>() {}));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public List<TaskBean> findSuccessors(long taskId)
    {
        try (Response response = target("/tasks/" + taskId + "/successors")
                .request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(new GenericType<List<TaskDto>>() {}));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public void delete(long id)
    {
        try (Response response = target("/tasks/" + id).request().delete())
        {
            requireSuccess(response);
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    private jakarta.ws.rs.client.WebTarget target(String path)
    {
        return client.target("http://" + host + ":" + port + "/pragma/api" + path);
    }

    private void requireSuccess(Response response)
    {
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL)
            throw new RuntimeException("HTTP " + response.getStatus() + " " + response.getStatusInfo().getReasonPhrase());
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
