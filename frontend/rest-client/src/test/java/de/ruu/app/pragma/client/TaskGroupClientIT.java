package de.ruu.app.pragma.client;

import de.ruu.app.pragma.dto.TaskGroupDto;
import de.ruu.lib.junit.DisabledOnServerNotListening;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnServerNotListening(propertyNameHost = "pragma.rest-api.host", propertyNamePort = "pragma.rest-api.port")
class TaskGroupClientIT
{
    private TaskGroupClient client;

    @BeforeEach
    void setUp()
    {
        client = new TaskGroupClient();
        client.postConstruct();
    }

    @AfterEach
    void tearDown()
    {
        client.preDestroy();
    }

    @Test
    void testFindAll()
    {
        List<TaskGroupDto> groups = client.findAll();
        assertThat(groups).isNotNull();
    }

    @Test
    void testCreateAndDelete()
    {
        String name = "it-create-" + System.currentTimeMillis();
        TaskGroupDto created = client.create(new TaskGroupDto(name));

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo(name);

        client.delete(created.id());

        Optional<TaskGroupDto> found = client.findById(created.id());
        assertThat(found).isEmpty();
    }

    @Test
    void testUpdate()
    {
        String originalName = "it-update-orig-" + System.currentTimeMillis();
        TaskGroupDto created = client.create(new TaskGroupDto(originalName));
        assertThat(created.id()).isNotNull();

        String updatedName = "it-update-new-" + System.currentTimeMillis();
        TaskGroupDto updated = client.update(created.id(), new TaskGroupDto(updatedName));

        assertThat(updated.name()).isEqualTo(updatedName);

        client.delete(created.id());
    }

    @Test
    void testFindById()
    {
        String name = "it-findbyid-" + System.currentTimeMillis();
        TaskGroupDto created = client.create(new TaskGroupDto(name));
        assertThat(created.id()).isNotNull();

        Optional<TaskGroupDto> found = client.findById(created.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo(name);

        client.delete(created.id());
    }
}
