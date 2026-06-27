package de.ruu.app.pragma.client;

import de.ruu.app.pragma.bean.TaskGroupBean;
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
        List<TaskGroupBean> groups = client.findAll();
        assertThat(groups).isNotNull();
    }

    @Test
    void testCreateAndDelete()
    {
        String name = "it-create-" + System.currentTimeMillis();
        TaskGroupBean created = client.create(new TaskGroupBean(name));

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo(name);

        client.delete(created);

        Optional<TaskGroupBean> found = client.findById(created.id());
        assertThat(found).isEmpty();
    }

    @Test
    void testUpdate()
    {
        String originalName = "it-update-orig-" + System.currentTimeMillis();
        TaskGroupBean created = client.create(new TaskGroupBean(originalName));
        assertThat(created.id()).isNotNull();

        String updatedName = "it-update-new-" + System.currentTimeMillis();
        created.name(updatedName);
        TaskGroupBean updated = client.update(created);

        assertThat(updated.name()).isEqualTo(updatedName);

        client.delete(created);
    }

    @Test
    void testFindById()
    {
        String name = "it-findbyid-" + System.currentTimeMillis();
        TaskGroupBean created = client.create(new TaskGroupBean(name));
        assertThat(created.id()).isNotNull();

        Optional<TaskGroupBean> found = client.findById(created.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo(created.name());

        client.delete(created);
    }
}
