package de.ruu.app.pragma.bean;

import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;

import java.util.IdentityHashMap;
import java.util.Map;

public final class Mappings
{
    private Mappings() {}

    // ── DTO → Bean ─────────────────────────────────────────────────────────

    public static TaskGroupBean toBean(TaskGroupDto in)
    {
        return toBean(in, new IdentityHashMap<>());
    }

    static TaskGroupBean toBean(TaskGroupDto in, Map<Object, Object> ctx)
    {
        TaskGroupBean cached = (TaskGroupBean) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupBean out = new TaskGroupBean(in);
        ctx.put(in, out);

        in.tasks().ifPresent(tasks -> tasks.forEach(t -> toBean(t, ctx)));

        return out;
    }

    static TaskBean toBean(TaskDto in, Map<Object, Object> ctx)
    {
        TaskBean cached = (TaskBean) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupBean group = (TaskGroupBean) ctx.get(in.taskGroup().orElseThrow());
        if (group == null) group = toBean(in.taskGroup().orElseThrow(), ctx);

        TaskBean out = new TaskBean(group, in);
        ctx.put(in, out);

        in.parentTask().ifPresent(p -> {
            TaskBean parentBean = (TaskBean) ctx.get(p);
            if (parentBean == null) parentBean = toBean(p, ctx);
            out.parentTask(parentBean);
        });

        in.subTasks().ifPresent(children -> children.forEach(child -> {
            TaskBean childBean = (TaskBean) ctx.get(child);
            if (childBean == null) childBean = toBean(child, ctx);
            out.addSubTask(childBean);
        }));

        in.predecessors().ifPresent(preds -> preds.forEach(pred -> {
            TaskBean predBean = (TaskBean) ctx.get(pred);
            if (predBean == null) predBean = toBean(pred, ctx);
            out.addPredecessor(predBean);
        }));

        in.successors().ifPresent(succs -> succs.forEach(succ -> {
            TaskBean succBean = (TaskBean) ctx.get(succ);
            if (succBean == null) succBean = toBean(succ, ctx);
            out.addSuccessor(succBean);
        }));

        return out;
    }

    // ── Bean → DTO ─────────────────────────────────────────────────────────

    public static TaskGroupDto toDto(TaskGroupBean in)
    {
        return toDto(in, new IdentityHashMap<>());
    }

    static TaskGroupDto toDto(TaskGroupBean in, Map<Object, Object> ctx)
    {
        TaskGroupDto cached = (TaskGroupDto) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupDto out = new TaskGroupDto(in.name());
        ctx.put(in, out);

        in.tasks().ifPresent(tasks -> tasks.forEach(t -> toDto(t, ctx)));

        return out;
    }

    static TaskDto toDto(TaskBean in, Map<Object, Object> ctx)
    {
        TaskDto cached = (TaskDto) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupDto group = (TaskGroupDto) ctx.get(in.taskGroup().orElseThrow());
        if (group == null) group = toDto(in.taskGroup().orElseThrow(), ctx);

        TaskDto out = new TaskDto(in.name(), group);
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

        return out;
    }
}
