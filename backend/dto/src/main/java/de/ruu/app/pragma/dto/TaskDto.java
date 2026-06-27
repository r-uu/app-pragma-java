package de.ruu.app.pragma.dto;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import de.ruu.app.pragma.core.Task;
import de.ruu.app.pragma.core.TaskEntity;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "jsonId", scope = TaskDto.class)
public class TaskDto implements Task<TaskGroupDto, TaskDto>
{
    private final  UUID            jsonId  = UUID.randomUUID();
    private @Nullable Long         id;
    private @Nullable Short        version;
    private @NotBlank  String       name;
    private @Nullable TaskDto      parentTask;
    private           TaskGroupDto taskGroup;
    private @Nullable Set<TaskDto> subTasks;      // null = not yet loaded
    private @Nullable Set<TaskDto> predecessors;  // null = not yet loaded
    private @Nullable Set<TaskDto> successors;    // null = not yet loaded
    private @Nullable String       description;
    private @Nullable LocalDate    plannedStart;
    private @Nullable LocalDate    plannedEnd;
    private           Boolean      closed       = false;

    /** For JSON deserialization only. */
    protected TaskDto() { name = ""; }

    public TaskDto(String name, TaskGroupDto taskGroup)
    {
        this.name = Objects.requireNonNull(name,      "name");
        Objects.requireNonNull(taskGroup, "taskGroup");
        taskGroup.addTask(this);
    }

    /** Mapping constructor — copies id, version and name from any TaskEntity (e.g. TaskJPA). */
    public TaskDto(TaskGroupDto group, TaskEntity<?, ?> in)
    {
        this.id      = in.id();
        this.version = in.version();
        this.name    = in.name();
        // direct field set instead of group.addTask(this) — avoids circular ref
        // (group.tasks → task → taskGroup → group.tasks) during JSON serialization
        this.taskGroup = Objects.requireNonNull(group, "group");
    }

    /** Package-private — called exclusively by TaskGroupDto.addTask() to avoid recursion. */
    void taskGroupInternal(TaskGroupDto group) { this.taskGroup = group; }

    @Override public @Nullable Long                   id()                                 { return id;                                                        }
    public    @Nullable Short                         version()                            { return version;                                                   }
    @Override public           String                 name()                               { return name;                                                      }
    public             TaskDto                        id     (@Nullable Long  id)          { this.id      = id;      return this; }
    public             TaskDto                        version(@Nullable Short v)           { this.version = v;       return this; }
    @Override public           TaskDto                name(String name)                    { this.name = Objects.requireNonNull(name, "name"); return this; }
    @Override public           Optional<TaskDto>      parentTask()                         { return Optional.ofNullable(parentTask); }
    @Override public           TaskDto                parentTask(@Nullable TaskDto parent) { this.parentTask = parent; return this;  }
    @Override public           Optional<Set<TaskDto>> subTasks()                           { return Optional.ofNullable(subTasks);     }
    @Override public           Optional<Set<TaskDto>> predecessors()                       { return Optional.ofNullable(predecessors); }
    @Override public           Optional<Set<TaskDto>> successors()                         { return Optional.ofNullable(successors);   }

    @Override public Optional<String>    description () { return Optional.ofNullable(description);  }
    @Override public Optional<LocalDate> plannedStart() { return Optional.ofNullable(plannedStart); }
    @Override public Optional<LocalDate> plannedEnd  () { return Optional.ofNullable(plannedEnd);   }
    @Override public Boolean             closed      () { return closed;                             }

    @Override public TaskDto description (@Nullable String    d) { this.description = d; return this; }
    @Override public TaskDto plannedStart(@Nullable LocalDate d) { this.plannedStart = d; return this; }
    @Override public TaskDto plannedEnd  (@Nullable LocalDate d) { this.plannedEnd   = d; return this; }
    @Override public TaskDto closed      (          Boolean   c) { this.closed       = c; return this; }

    @Override
    public TaskGroupDto taskGroup() { return taskGroup; }

    @Override
    public TaskDto taskGroup(TaskGroupDto group)
    {
        if (Objects.requireNonNull(group, "group") != this.taskGroup) group.addTask(this);
        return this;
    }

    @Override
    public void addSubTask(TaskDto child)
    {
        Objects.requireNonNull(child, "child");
        if (subTasks == null) subTasks = new LinkedHashSet<>();
        if (subTasks.add(child)) child.parentTask(this);
    }

    @Override
    public void removeSubTask(TaskDto child)
    {
        Objects.requireNonNull(child, "child");
        if (subTasks != null && subTasks.remove(child))
            child.parentTask().filter(p -> p == this).ifPresent(p -> child.parentTask(null));
    }

    @Override
    public void addPredecessor(TaskDto predecessor)
    {
        Objects.requireNonNull(predecessor, "predecessor");
        if (predecessors == null) predecessors = new LinkedHashSet<>();
        if (predecessors.add(predecessor)) predecessor.addSuccessor(this);
    }

    @Override
    public void removePredecessor(TaskDto predecessor)
    {
        Objects.requireNonNull(predecessor, "predecessor");
        if (predecessors != null && predecessors.remove(predecessor))
            predecessor.removeSuccessor(this);
    }

    @Override
    public void addSuccessor(TaskDto successor)
    {
        Objects.requireNonNull(successor, "successor");
        if (successors == null) successors = new LinkedHashSet<>();
        if (successors.add(successor)) successor.addPredecessor(this);
    }

    @Override
    public void removeSuccessor(TaskDto successor)
    {
        Objects.requireNonNull(successor, "successor");
        if (successors != null && successors.remove(successor))
            successor.removePredecessor(this);
    }
}
