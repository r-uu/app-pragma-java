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
import de.ruu.app.pragma.bean.TaskGroupBean;
import de.ruu.app.pragma.dto.TaskGroupDto;
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

import java.util.List;
import java.util.Optional;

/**
 * REST client for task-group operations.
 *
 * <p>The public interface works exclusively with {@link TaskGroupBean}.
 * DTO types are an internal transport detail.
 */
@Singleton
public class TaskGroupClient
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

    public List<TaskGroupBean> findAll()
    {
        try (Response response = target("/task-groups").request(MediaType.APPLICATION_JSON).get())
        {
            requireSuccess(response);
            return response.readEntity(new GenericType<List<TaskGroupDto>>() {})
                           .stream().map(Mappings::toBean).toList();
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public Optional<TaskGroupBean> findById(long id)
    {
        try (Response response = target("/task-groups/" + id).request(MediaType.APPLICATION_JSON).get())
        {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) return Optional.empty();
            requireSuccess(response);
            return Optional.of(Mappings.toBean(response.readEntity(TaskGroupDto.class)));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public TaskGroupBean create(TaskGroupBean bean)
    {
        try (Response response = target("/task-groups")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Mappings.toDto(bean))))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskGroupDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public TaskGroupBean update(long id, TaskGroupBean bean)
    {
        try (Response response = target("/task-groups/" + id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Mappings.toDto(bean))))
        {
            requireSuccess(response);
            return Mappings.toBean(response.readEntity(TaskGroupDto.class));
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public void delete(long id)
    {
        try (Response response = target("/task-groups/" + id).request().delete())
        {
            requireSuccess(response);
        }
        catch (ProcessingException e) { throw new RuntimeException("communication error", e); }
    }

    public void deleteAll()
    {
        try (Response response = target("/task-groups").request().delete())
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
}
