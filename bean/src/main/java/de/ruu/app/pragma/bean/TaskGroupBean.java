package de.ruu.app.pragma.bean;

import de.ruu.app.pragma.core.TaskGroup;
import de.ruu.app.pragma.dto.TaskGroupDto;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TaskGroupBean implements TaskGroup<TaskBean>
{
    private @Nullable Long          id;
    private @Nullable Short         version;
    private           String        name;
    private @Nullable Set<TaskBean> tasks; // null = not yet loaded

    public TaskGroupBean(String name) { this.name = Objects.requireNonNull(name, "name"); }

    /** Mapping constructor — copies id, version and name from a TaskGroupDto. */
    public TaskGroupBean(TaskGroupDto in)
    {
        this.id      = in.id();
        this.version = in.version();
        this.name    = in.name();
    }

    @Override public @Nullable Long                    id()              { return id;                         }
    public    @Nullable Short                          version()         { return version;                    }
    @Override public           String                  name()            { return name;                       }
    @Override public           TaskGroupBean           name(String name) { this.name = Objects.requireNonNull(name, "name"); return this; }
    @Override public           Optional<Set<TaskBean>> tasks()           { return Optional.ofNullable(tasks); }

    @Override
    public void addTask(TaskBean task)
    {
        Objects.requireNonNull(task, "task");
        if (tasks == null) tasks = new LinkedHashSet<>();
        if (tasks.add(task)) {
            task.taskGroup()
                .filter(old -> old != this)
                .ifPresent(old -> old.tasks().ifPresent(t -> t.remove(task)));
            task.taskGroupInternal(this);
        }
    }

    @Override
    public void removeTask(TaskBean task)
    {
        Objects.requireNonNull(task, "task");
        if (tasks != null) tasks.remove(task);
    }
}
