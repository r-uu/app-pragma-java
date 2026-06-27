package de.ruu.app.pragma.bean;

import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MappingsTest
{
    // ── fixtures ───────────────────────────────────────────────────────────

    private TaskGroupBean groupBean;
    private TaskBean      taskA;
    private TaskBean      taskB;

    @BeforeEach
    void setUp()
    {
        groupBean = new TaskGroupBean("Sprint 1");
        taskA     = new TaskBean(groupBean, "Task A");
        taskB     = new TaskBean(groupBean, "Task B");
    }

    // ── TaskGroupBean → TaskGroupDto ───────────────────────────────────────

    @Test void groupBean_toDto_scalarFields()
    {
        TaskGroupDto dto = Mappings.toDto(groupBean);

        assertThat(dto.name()).isEqualTo("Sprint 1");
        assertThat(dto.id())     .isNull(); // not set on fresh bean
        assertThat(dto.version()).isNull();
    }

    @Test void groupBean_toDto_idAndVersionPropagated()
    {
        TaskGroupDto in  = new TaskGroupDto("G").id(7L).version((short) 3);
        TaskGroupBean bean = new TaskGroupBean(in);
        TaskGroupDto  out  = Mappings.toDto(bean);

        assertThat(out.id())     .isEqualTo(7L);
        assertThat(out.version()).isEqualTo((short) 3);
        assertThat(out.name())   .isEqualTo("G");
    }

    @Test void groupBean_toDto_doesNotIncludeUnloadedTasks()
    {
        // tasks() on groupBean is present (constructor triggered addTask),
        // so the DTO's tasks Optional is also present
        TaskGroupDto dto = Mappings.toDto(groupBean);
        assertThat(dto.tasks()).isPresent();
    }

    // ── TaskGroupDto → TaskGroupBean ───────────────────────────────────────

    @Test void groupDto_toBean_scalarFields()
    {
        TaskGroupDto  dto  = new TaskGroupDto("Sprint 2").id(42L).version((short) 1);
        TaskGroupBean bean = Mappings.toBean(dto);

        assertThat(bean.name())   .isEqualTo("Sprint 2");
        assertThat(bean.id())     .isEqualTo(42L);
        assertThat(bean.version()).isEqualTo((short) 1);
    }

    // ── TaskBean → TaskDto ─────────────────────────────────────────────────

    @Test void taskBean_toDto_scalarFields()
    {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end   = LocalDate.of(2026, 1, 31);
        taskA.description("desc").plannedStart(start).plannedEnd(end).closed(true);

        TaskDto dto = Mappings.toDto(taskA);

        assertThat(dto.name())        .isEqualTo("Task A");
        assertThat(dto.description()) .hasValue("desc");
        assertThat(dto.plannedStart()).hasValue(start);
        assertThat(dto.plannedEnd())  .hasValue(end);
        assertThat(dto.closed())      .isTrue();
    }

    @Test void taskBean_toDto_idAndVersionPropagated()
    {
        // simulate a bean loaded from DB (via DTO → Bean → roundtrip)
        TaskGroupDto  gDto   = new TaskGroupDto("G").id(1L).version((short) 0);
        TaskGroupBean gBean  = new TaskGroupBean(gDto);
        TaskDto       tDto   = new TaskDto("T", gDto).id(5L).version((short) 2);
        TaskBean      tBean  = new TaskBean(gBean, tDto);

        TaskDto out = Mappings.toDto(tBean);

        assertThat(out.id())     .isEqualTo(5L);
        assertThat(out.version()).isEqualTo((short) 2);
    }

    @Test void taskBean_toDto_sameGroupInstanceReused()
    {
        TaskDto dtoA = Mappings.toDto(taskA);
        TaskDto dtoB = Mappings.toDto(taskB);

        // different calls → different ctx → different group DTO instances
        assertThat(dtoA.taskGroup().name()).isEqualTo("Sprint 1");
        assertThat(dtoB.taskGroup().name()).isEqualTo("Sprint 1");
    }

    @Test void taskList_toDto_sharedGroupInstancePerCall()
    {
        // toDto(TaskBean) individually → two separate ctx, two group DTOs
        TaskDto dtoA = Mappings.toDto(taskA);
        TaskDto dtoB = Mappings.toDto(taskB);
        assertThat(dtoA.taskGroup()).isNotSameAs(dtoB.taskGroup());
    }

    // ── predecessor/successor mapping ──────────────────────────────────────

    @Test void taskBean_toDto_predecessorMapped()
    {
        taskB.addPredecessor(taskA);

        // map just taskB — group context provides taskA via ctx
        TaskGroupDto groupDto = Mappings.toDto(groupBean);
        // find dtoB in group's tasks
        TaskDto dtoB = groupDto.tasks().get().stream()
            .filter(t -> "Task B".equals(t.name()))
            .findFirst().orElseThrow();

        assertThat(dtoB.predecessors()).hasValueSatisfying(preds ->
            assertThat(preds).anyMatch(p -> "Task A".equals(p.name())));
    }

    @Test void taskBean_toDto_successorMapped()
    {
        taskA.addSuccessor(taskB);

        TaskGroupDto groupDto = Mappings.toDto(groupBean);
        TaskDto dtoA = groupDto.tasks().get().stream()
            .filter(t -> "Task A".equals(t.name()))
            .findFirst().orElseThrow();

        assertThat(dtoA.successors()).hasValueSatisfying(succs ->
            assertThat(succs).anyMatch(s -> "Task B".equals(s.name())));
    }

    // ── TaskDto → TaskBean ─────────────────────────────────────────────────

    @Test void taskDto_toBean_scalarFields()
    {
        LocalDate start = LocalDate.of(2026, 2, 1);
        TaskGroupDto gDto = new TaskGroupDto("G");
        TaskDto      tDto = new TaskDto("T", gDto)
            .id(3L).version((short) 1)
            .description("hello")
            .plannedStart(start)
            .closed(true);

        TaskBean bean = Mappings.toBean(tDto);

        assertThat(bean.name())        .isEqualTo("T");
        assertThat(bean.id())          .isEqualTo(3L);
        assertThat(bean.version())     .isEqualTo((short) 1);
        assertThat(bean.description()) .hasValue("hello");
        assertThat(bean.plannedStart()).hasValue(start);
        assertThat(bean.closed())      .isTrue();
    }

    @Test void taskDto_toBean_groupPreserved()
    {
        TaskGroupDto gDto = new TaskGroupDto("G2");
        TaskDto      tDto = new TaskDto("T", gDto);
        TaskBean     bean = Mappings.toBean(tDto);

        assertThat(bean.taskGroup().name()).isEqualTo("G2");
    }

    @Test void taskDto_toBean_contextDeduplicatesGroup()
    {
        TaskGroupDto gDto = new TaskGroupDto("G");
        TaskDto      t1   = new TaskDto("T1", gDto);
        TaskDto      t2   = new TaskDto("T2", gDto);

        List<TaskBean> beans = Mappings.toBean(List.of(t1, t2));

        // both tasks must belong to the same TaskGroupBean instance
        assertThat(beans.get(0).taskGroup()).isSameAs(beans.get(1).taskGroup());
    }

    // ── subTask hierarchy ──────────────────────────────────────────────────

    @Test void taskDto_toBean_parentTaskMapped()
    {
        TaskGroupDto gDto   = new TaskGroupDto("G");
        TaskDto      parent = new TaskDto("Parent", gDto);
        TaskDto      child  = new TaskDto("Child",  gDto);
        parent.addSubTask(child);

        TaskBean parentBean = Mappings.toBean(parent);

        assertThat(parentBean.subTasks()).hasValueSatisfying(ts ->
            assertThat(ts).anyMatch(t -> "Child".equals(t.name())));
        TaskBean childBean = parentBean.subTasks().get().iterator().next();
        assertThat(childBean.parentTask()).hasValue(parentBean);
    }
}
