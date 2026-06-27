package de.ruu.app.pragma.bean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TaskGroupBeanTest
{
    // ── constructor ────────────────────────────────────────────────────────

    @Test void constructor_setsName()
    {
        assertThat(new TaskGroupBean("My Group").name()).isEqualTo("My Group");
    }

    @Test void constructor_requiresNonNullName()
    {
        assertThatNullPointerException().isThrownBy(() -> new TaskGroupBean((String) null));
    }

    @Test void freshGroup_tasksIsEmptyOptional()
    {
        assertThat(new TaskGroupBean("G").tasks()).isEmpty();
    }

    // ── addTask ────────────────────────────────────────────────────────────

    @Test void addTask_registersTask()
    {
        TaskGroupBean g = new TaskGroupBean("G");
        TaskBean      t = new TaskBean(g, "T");

        assertThat(g.tasks()).hasValueSatisfying(ts -> assertThat(ts).containsExactly(t));
    }

    @Test void addTask_setsTaskGroupOnTask()
    {
        TaskGroupBean g = new TaskGroupBean("G");
        TaskBean      t = new TaskBean(g, "T");

        assertThat(t.taskGroup()).isSameAs(g);
    }

    @Test void addTask_isIdempotent()
    {
        TaskGroupBean g = new TaskGroupBean("G");
        TaskBean      t = new TaskBean(g, "T");
        g.addTask(t); // second call

        assertThat(g.tasks()).hasValueSatisfying(ts -> assertThat(ts).hasSize(1));
    }

    @Test void addTask_requiresNonNull()
    {
        TaskGroupBean g = new TaskGroupBean("G");
        assertThatNullPointerException().isThrownBy(() -> g.addTask(null));
    }

    // ── removeTask ─────────────────────────────────────────────────────────

    @Test void removeTask_removesFromGroup()
    {
        TaskGroupBean g = new TaskGroupBean("G");
        TaskBean      t = new TaskBean(g, "T");
        g.removeTask(t);

        assertThat(g.tasks()).hasValueSatisfying(ts -> assertThat(ts).isEmpty());
    }

    @Test void removeTask_onUnloadedSet_doesNothing()
    {
        TaskGroupBean g1 = new TaskGroupBean("G1");
        TaskGroupBean g2 = new TaskGroupBean("G2");
        TaskBean      t  = new TaskBean(g1, "T");

        g2.removeTask(t); // g2.tasks is null — must not throw
        assertThat(g2.tasks()).isEmpty();
    }

    // ── moving a task between groups ───────────────────────────────────────

    @Test void taskGroup_setter_movesTaskToNewGroup()
    {
        TaskGroupBean g1 = new TaskGroupBean("G1");
        TaskGroupBean g2 = new TaskGroupBean("G2");
        TaskBean      t  = new TaskBean(g1, "T");

        t.taskGroup(g2);

        assertThat(t.taskGroup()).isSameAs(g2);
        assertThat(g2.tasks()).hasValueSatisfying(ts -> assertThat(ts).contains(t));
        assertThat(g1.tasks()).hasValueSatisfying(ts -> assertThat(ts).doesNotContain(t));
    }

    // ── name setter ────────────────────────────────────────────────────────

    @Test void name_setter_updatesName()
    {
        TaskGroupBean g = new TaskGroupBean("old");
        g.name("new");
        assertThat(g.name()).isEqualTo("new");
    }

    @Test void name_setter_returnsThis()
    {
        TaskGroupBean g = new TaskGroupBean("G");
        assertThat(g.name("X")).isSameAs(g);
    }

    @Test void name_setter_requiresNonNull()
    {
        TaskGroupBean g = new TaskGroupBean("G");
        assertThatNullPointerException().isThrownBy(() -> g.name(null));
    }
}
