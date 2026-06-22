package de.ruu.app.pragma.client;

import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;
import de.ruu.lib.junit.DisabledOnServerNotListening;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnServerNotListening(propertyNameHost = "pragma.rest-api.host", propertyNamePort = "pragma.rest-api.port")
class TaskClientIT
{
    private TaskGroupClient groupClient;
    private TaskClient      taskClient;
    private TaskGroupDto    testGroup;

    @BeforeEach
    void setUp()
    {
        groupClient = new TaskGroupClient();
        groupClient.postConstruct();
        taskClient = new TaskClient();
        taskClient.postConstruct();

        testGroup = groupClient.create(new TaskGroupDto("it-task-group-" + System.currentTimeMillis()));
    }

    @AfterEach
    void tearDown()
    {
        if (testGroup != null && testGroup.id() != null)
            groupClient.delete(testGroup.id());
        taskClient.preDestroy();
        groupClient.preDestroy();
    }

    @Test
    void testFindAllByGroup()
    {
        List<TaskDto> tasks = taskClient.findAll(testGroup.id());
        assertThat(tasks).isNotNull();
    }

    @Test
    void testCreateAndDelete()
    {
        String name = "it-task-" + System.currentTimeMillis();
        TaskDto created = taskClient.create(name, testGroup.id());

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo(name);

        taskClient.delete(created.id());

        Optional<TaskDto> found = taskClient.findById(created.id());
        assertThat(found).isEmpty();
    }

    @Test
    void testUpdate()
    {
        TaskDto created = taskClient.create("it-update-orig-" + System.currentTimeMillis(), testGroup.id());
        assertThat(created.id()).isNotNull();

        created.name("it-update-new-" + System.currentTimeMillis());
        TaskDto updated = taskClient.update(created.id(), created);

        assertThat(updated.name()).isEqualTo(created.name());

        taskClient.delete(created.id());
    }

    @Test
    void testFindById()
    {
        TaskDto created = taskClient.create("it-findbyid-" + System.currentTimeMillis(), testGroup.id());
        assertThat(created.id()).isNotNull();

        Optional<TaskDto> found = taskClient.findById(created.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo(created.name());

        taskClient.delete(created.id());
    }
}
