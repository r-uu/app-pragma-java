package de.ruu.app.pragma.bean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TaskBeanTest
{
    private TaskGroupBean group;
    private TaskBean      a;
    private TaskBean      b;
    private TaskBean      c;

    @BeforeEach
    void setUp()
    {
        group = new TaskGroupBean("G");
        a     = new TaskBean(group, "A");
        b     = new TaskBean(group, "B");
        c     = new TaskBean(group, "C");
    }

    // ── constructor ────────────────────────────────────────────────────────

    @Test void constructor_registersTaskInGroup()
    {
        assertThat(group.tasks()).hasValueSatisfying(ts -> assertThat(ts).contains(a, b, c));
    }

    @Test void constructor_setsTaskGroupOnTask()
    {
        assertThat(a.taskGroup()).isSameAs(group);
    }

    @Test void constructor_requiresNonNullName()
    {
        assertThatNullPointerException().isThrownBy(() -> new TaskBean(group, (String) null));
    }

    @Test void constructor_requiresNonNullGroup()
    {
        assertThatNullPointerException().isThrownBy(() -> new TaskBean(null, "X"));
    }

    // ── addPredecessor ─────────────────────────────────────────────────────

    @Test void addPredecessor_setsBothSides()
    {
        b.addPredecessor(a);

        assertThat(b.predecessors()).hasValueSatisfying(ps -> assertThat(ps).containsExactly(a));
        assertThat(a.successors())  .hasValueSatisfying(ss -> assertThat(ss).containsExactly(b));
    }

    @Test void addPredecessor_isIdempotent()
    {
        b.addPredecessor(a);
        b.addPredecessor(a);

        assertThat(b.predecessors()).hasValueSatisfying(ps -> assertThat(ps).hasSize(1));
        assertThat(a.successors())  .hasValueSatisfying(ss -> assertThat(ss).hasSize(1));
    }

    @Test void addPredecessor_multiplePredsAllRegistered()
    {
        c.addPredecessor(a);
        c.addPredecessor(b);

        assertThat(c.predecessors()).hasValueSatisfying(ps -> assertThat(ps).containsExactlyInAnyOrder(a, b));
        assertThat(a.successors())  .hasValueSatisfying(ss -> assertThat(ss).containsExactly(c));
        assertThat(b.successors())  .hasValueSatisfying(ss -> assertThat(ss).containsExactly(c));
    }

    // ── removePredecessor ──────────────────────────────────────────────────

    @Test void removePredecessor_removesBothSides()
    {
        b.addPredecessor(a);
        b.removePredecessor(a);

        assertThat(b.predecessors()).hasValueSatisfying(ps -> assertThat(ps).isEmpty());
        assertThat(a.successors())  .hasValueSatisfying(ss -> assertThat(ss).isEmpty());
    }

    @Test void removePredecessor_onUnloadedSet_doesNothing()
    {
        // predecessors was never loaded — Optional.empty()
        assertThat(a.predecessors()).isEmpty();
        a.removePredecessor(b); // must not throw
        assertThat(a.predecessors()).isEmpty();
    }

    // ── addSuccessor ───────────────────────────────────────────────────────

    @Test void addSuccessor_setsBothSides()
    {
        a.addSuccessor(b);

        assertThat(a.successors())   .hasValueSatisfying(ss -> assertThat(ss).containsExactly(b));
        assertThat(b.predecessors()) .hasValueSatisfying(ps -> assertThat(ps).containsExactly(a));
    }

    @Test void addSuccessor_isIdempotent()
    {
        a.addSuccessor(b);
        a.addSuccessor(b);

        assertThat(a.successors())  .hasValueSatisfying(ss -> assertThat(ss).hasSize(1));
        assertThat(b.predecessors()).hasValueSatisfying(ps -> assertThat(ps).hasSize(1));
    }

    // ── removeSuccessor ────────────────────────────────────────────────────

    @Test void removeSuccessor_removesBothSides()
    {
        a.addSuccessor(b);
        a.removeSuccessor(b);

        assertThat(a.successors())  .hasValueSatisfying(ss -> assertThat(ss).isEmpty());
        assertThat(b.predecessors()).hasValueSatisfying(ps -> assertThat(ps).isEmpty());
    }

    // ── addSubTask / removeSubTask ─────────────────────────────────────────

    @Test void addSubTask_setsParentOnChild()
    {
        a.addSubTask(b);

        assertThat(a.subTasks()).hasValueSatisfying(ts -> assertThat(ts).containsExactly(b));
        assertThat(b.parentTask()).hasValue(a);
    }

    @Test void addSubTask_isIdempotent()
    {
        a.addSubTask(b);
        a.addSubTask(b);

        assertThat(a.subTasks()).hasValueSatisfying(ts -> assertThat(ts).hasSize(1));
    }

    @Test void removeSubTask_clearsParent()
    {
        a.addSubTask(b);
        a.removeSubTask(b);

        assertThat(a.subTasks()).hasValueSatisfying(ts -> assertThat(ts).isEmpty());
        assertThat(b.parentTask()).isEmpty();
    }

    @Test void removeSubTask_onUnloadedSet_doesNothing()
    {
        assertThat(a.subTasks()).isEmpty();
        a.removeSubTask(b); // must not throw
        assertThat(a.subTasks()).isEmpty();
    }

    // ── Optional semantics ─────────────────────────────────────────────────

    @Test void unloadedRelations_returnEmptyOptional()
    {
        // fresh task — no relation ever touched
        TaskBean fresh = new TaskBean(group, "Fresh");
        assertThat(fresh.predecessors()).isEmpty();
        assertThat(fresh.successors())  .isEmpty();
        assertThat(fresh.subTasks())    .isEmpty();
        assertThat(fresh.parentTask())  .isEmpty();
    }

    @Test void loadedButEmptyRelation_returnsPresentOptionalWithEmptySet()
    {
        // touching predecessors initialises the set
        b.addPredecessor(a);
        b.removePredecessor(a);

        assertThat(b.predecessors()).isPresent();
        assertThat(b.predecessors()).hasValueSatisfying(ps -> assertThat(ps).isEmpty());
    }

    // ── scalar setters ─────────────────────────────────────────────────────

    @Test void scalarSetters_returnThis()
    {
        LocalDate date = LocalDate.of(2026, 1, 1);
        assertThat(a.name("A2"))          .isSameAs(a);
        assertThat(a.description("d"))    .isSameAs(a);
        assertThat(a.plannedStart(date))  .isSameAs(a);
        assertThat(a.plannedEnd(date))    .isSameAs(a);
        assertThat(a.closed(true))        .isSameAs(a);
    }

    @Test void name_requiresNonNull()
    {
        assertThatNullPointerException().isThrownBy(() -> a.name(null));
    }
}
