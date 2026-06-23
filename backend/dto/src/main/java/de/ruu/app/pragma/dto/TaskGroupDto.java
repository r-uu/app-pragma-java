package de.ruu.app.pragma.dto;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import de.ruu.app.pragma.core.TaskGroup;
import de.ruu.app.pragma.core.TaskGroupEntity;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "jsonId", scope = TaskGroupDto.class)
public class TaskGroupDto implements TaskGroup<TaskDto>
{
    private final  UUID            jsonId  = UUID.randomUUID();
    private @Nullable Long         id;
    private @Nullable Short        version;
    private           String       name;
    private @Nullable Set<TaskDto> tasks; // null = not yet loaded

    /** For JSON deserialization only. */
    protected TaskGroupDto() { name = ""; }

    public TaskGroupDto(String name) { this.name = Objects.requireNonNull(name, "name"); }

    /** Mapping constructor — copies id, version and name from any TaskGroupEntity (e.g. TaskGroupJPA). */
    public TaskGroupDto(TaskGroupEntity<?> in)
    {
        this.id      = in.id();
        this.version = in.version();
        this.name    = in.name();
    }

    @Override public @Nullable Long                   id()              { return id;                         }
    public    @Nullable Short                         version()         { return version;                    }
    @Override public           String                 name()            { return name;                       }
    @Override public           TaskGroupDto           name(String name) { this.name = Objects.requireNonNull(name, "name"); return this; }
    @Override public           Optional<Set<TaskDto>> tasks()           { return Optional.ofNullable(tasks); }

    @Override
    public void addTask(TaskDto task)
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
    public void removeTask(TaskDto task)
    {
        Objects.requireNonNull(task, "task");
        if (tasks != null) tasks.remove(task);
    }
}
