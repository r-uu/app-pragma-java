package de.ruu.app.pragma.fx;

import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.bean.TaskGroupBean;

import java.util.IdentityHashMap;
import java.util.Map;

public final class Mappings
{
    private Mappings() {}

    // ── Bean → FX ──────────────────────────────────────────────────────────

    public static TaskGroupFx toFx(TaskGroupBean in)
    {
        return toFx(in, new IdentityHashMap<>());
    }

    static TaskGroupFx toFx(TaskGroupBean in, Map<Object, Object> ctx)
    {
        TaskGroupFx cached = (TaskGroupFx) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupFx out = new TaskGroupFx(in);
        ctx.put(in, out);

        in.tasks().ifPresent(tasks -> tasks.forEach(t -> toFx(t, ctx)));

        return out;
    }

    static TaskFx toFx(TaskBean in, Map<Object, Object> ctx)
    {
        TaskFx cached = (TaskFx) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupFx group = (TaskGroupFx) ctx.get(in.taskGroup().orElseThrow());
        if (group == null) group = toFx(in.taskGroup().orElseThrow(), ctx);

        TaskFx out = new TaskFx(group, in);
        ctx.put(in, out);

        in.parentTask().ifPresent(p -> {
            TaskFx parentFx = (TaskFx) ctx.get(p);
            if (parentFx == null) parentFx = toFx(p, ctx);
            out.parentTask(parentFx);
        });

        in.subTasks().ifPresent(children -> children.forEach(child -> {
            TaskFx childFx = (TaskFx) ctx.get(child);
            if (childFx == null) childFx = toFx(child, ctx);
            out.addSubTask(childFx);
        }));

        in.predecessors().ifPresent(preds -> preds.forEach(pred -> {
            TaskFx predFx = (TaskFx) ctx.get(pred);
            if (predFx == null) predFx = toFx(pred, ctx);
            out.addPredecessor(predFx);
        }));

        in.successors().ifPresent(succs -> succs.forEach(succ -> {
            TaskFx succFx = (TaskFx) ctx.get(succ);
            if (succFx == null) succFx = toFx(succ, ctx);
            out.addSuccessor(succFx);
        }));

        return out;
    }

    // ── FX → Bean ──────────────────────────────────────────────────────────

    public static TaskGroupBean toBean(TaskGroupFx in)
    {
        return toBean(in, new IdentityHashMap<>());
    }

    static TaskGroupBean toBean(TaskGroupFx in, Map<Object, Object> ctx)
    {
        TaskGroupBean cached = (TaskGroupBean) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupBean out = new TaskGroupBean(in.name());
        ctx.put(in, out);

        in.tasks().ifPresent(tasks -> tasks.forEach(t -> toBean(t, ctx)));

        return out;
    }

    static TaskBean toBean(TaskFx in, Map<Object, Object> ctx)
    {
        TaskBean cached = (TaskBean) ctx.get(in);
        if (cached != null) return cached;

        TaskGroupBean group = (TaskGroupBean) ctx.get(in.taskGroup().orElseThrow());
        if (group == null) group = toBean(in.taskGroup().orElseThrow(), ctx);

        TaskBean out = new TaskBean(in.name(), group);
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
}
