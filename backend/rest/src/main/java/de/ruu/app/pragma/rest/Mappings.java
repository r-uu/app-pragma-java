package de.ruu.app.pragma.rest;

import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;
import de.ruu.app.pragma.jpa.TaskGroupJPA;
import de.ruu.app.pragma.jpa.TaskJPA;

import java.util.IdentityHashMap;
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

        TaskGroupDto group = (TaskGroupDto) ctx.get(in.taskGroup().orElseThrow());
        if (group == null) group = toDto(in.taskGroup().orElseThrow(), ctx);

        TaskDto out = new TaskDto(group, in);
        ctx.put(in, out);

        // Only parentTask is set — subTasks/predecessors/successors are left null ("not loaded").
        // addSubTask/addPredecessor/addSuccessor create bidirectional DTO links that cause
        // circular references (e.g. task→parentTask→subTasks→task) during JSON serialization.
        // Clients reconstruct the tree from the flat task list using parentTask references.
        in.parentTask().ifPresent(p -> {
            TaskDto parentDto = (TaskDto) ctx.get(p);
            if (parentDto == null) parentDto = toDto(p, ctx);
            out.parentTask(parentDto);
        });

        out.plannedStart(in.plannedStart().orElse(null));
        out.plannedEnd  (in.plannedEnd()  .orElse(null));

        return out;
    }
}
