package de.ruu.app.pragma.rest;

import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;
import de.ruu.app.pragma.jpa.TaskGroupJPA;
import de.ruu.app.pragma.jpa.TaskJPA;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class Mappings
{
    private Mappings() {}

    // ── JPA → DTO ──────────────────────────────────────────────────────────

    static TaskGroupDto toDto(TaskGroupJPA in)
    {
        return toDto(in, new IdentityHashMap<>());
    }

    static TaskDto toDto(TaskJPA in)
    {
        return toDto(in, new IdentityHashMap<>());
    }

    static List<TaskDto> toDto(List<TaskJPA> in)
    {
        Map<Object, Object> ctx = new IdentityHashMap<>();
        return in.stream().map(t -> toDto(t, ctx)).toList();
    }

    static TaskGroupDto toDto(TaskGroupJPA in, Map<Object, Object> ctx)
    {
        TaskGroupDto cached = (TaskGroupDto) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupDto out = new TaskGroupDto(in);
        ctx.put(in, out);

        in.tasks().ifPresent(tasks -> tasks.forEach(t -> toDto(t, ctx)));

        return out;
    }

    static TaskDto toDto(TaskJPA in, Map<Object, Object> ctx)
    {
        TaskDto cached = (TaskDto) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupDto group = (TaskGroupDto) ctx.get(in.taskGroup());
        if (group == null) group = toDto(in.taskGroup(), ctx);

        TaskDto out = new TaskDto(group, in);
        ctx.put(in, out);

        in.parentTask().ifPresent(p -> {
            TaskDto parentDto = (TaskDto) ctx.get(p);
            if (parentDto == null) parentDto = toDto(p, ctx);
            out.parentTask(parentDto);
        });

        in.subTasks().ifPresent(children -> children.forEach(child -> {
            TaskDto childDto = (TaskDto) ctx.get(child);
            if (childDto == null) childDto = toDto(child, ctx);
            out.addSubTask(childDto);
        }));

        in.predecessors().ifPresent(preds -> preds.forEach(pred -> {
            TaskDto predDto = (TaskDto) ctx.get(pred);
            if (predDto == null) predDto = toDto(pred, ctx);
            out.addPredecessor(predDto);
        }));

        in.successors().ifPresent(succs -> succs.forEach(succ -> {
            TaskDto succDto = (TaskDto) ctx.get(succ);
            if (succDto == null) succDto = toDto(succ, ctx);
            out.addSuccessor(succDto);
        }));

        out.description (in.description().orElse(null));
        out.plannedStart(in.plannedStart().orElse(null));
        out.plannedEnd  (in.plannedEnd()  .orElse(null));
        out.closed      (in.closed());

        return out;
    }
}
