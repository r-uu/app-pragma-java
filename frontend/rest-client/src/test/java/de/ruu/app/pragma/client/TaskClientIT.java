package de.ruu.app.pragma.client;

import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.bean.TaskGroupBean;
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
    private TaskGroupBean   testGroup;

    @BeforeEach
    void setUp()
    {
        groupClient = new TaskGroupClient();
        groupClient.postConstruct();
        taskClient = new TaskClient();
        taskClient.postConstruct();

        testGroup = groupClient.create(new TaskGroupBean("it-task-group-" + System.currentTimeMillis()));
    }

    @AfterEach
    void tearDown()
    {
        if (testGroup != null && testGroup.id() != null)
            groupClient.delete(testGroup);
        taskClient.preDestroy();
        groupClient.preDestroy();
    }

    @Test
    void testFindAllByGroup()
    {
        List<TaskBean> tasks = taskClient.findAll(testGroup);
        assertThat(tasks).isNotNull();
    }

    @Test
    void testCreateAndDelete()
    {
        String name = "it-task-" + System.currentTimeMillis();
        TaskBean created = taskClient.create(new TaskBean(testGroup, name));

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo(name);

        taskClient.delete(created);

        Optional<TaskBean> found = taskClient.findById(created.id());
        assertThat(found).isEmpty();
    }

    @Test
    void testUpdate()
    {
        TaskBean created = taskClient.create(new TaskBean(testGroup, "it-update-orig-" + System.currentTimeMillis()));
        assertThat(created.id()).isNotNull();

        created.name("it-update-new-" + System.currentTimeMillis());
        TaskBean updated = taskClient.update(created);

        assertThat(updated.name()).isEqualTo(created.name());

        taskClient.delete(created);
    }

    @Test
    void testFindById()
    {
        TaskBean created = taskClient.create(new TaskBean(testGroup, "it-findbyid-" + System.currentTimeMillis()));
        assertThat(created.id()).isNotNull();

        Optional<TaskBean> found = taskClient.findById(created.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo(created.name());

        taskClient.delete(created);
    }
}
