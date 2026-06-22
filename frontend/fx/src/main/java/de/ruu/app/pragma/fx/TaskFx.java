package de.ruu.app.pragma.fx;

import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.core.Task;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TaskFx implements Task<TaskGroupFx, TaskFx>
{
    private final LongProperty                       id           = new SimpleLongProperty();
    private @Nullable Short                          version;
    private final StringProperty                     name         = new SimpleStringProperty();
    private final ObjectProperty<@Nullable TaskFx>   parentTask   = new SimpleObjectProperty<>();
    private final ObjectProperty<TaskGroupFx>        taskGroup    = new SimpleObjectProperty<>();
    private @Nullable ObservableSet<TaskFx>          subTasks;     // null = not loaded
    private @Nullable ObservableSet<TaskFx>          predecessors; // null = not loaded
    private @Nullable ObservableSet<TaskFx>          successors;   // null = not loaded

    public LongProperty                         idProperty()              { return id;          }
    public StringProperty                       nameProperty()            { return name;        }
    public ObjectProperty<@Nullable TaskFx>     parentTaskProperty()      { return parentTask;  }
    public ObjectProperty<TaskGroupFx>          taskGroupProperty()       { return taskGroup;   }
    public @Nullable ObservableSet<TaskFx>      subTasksObservable()      { return subTasks;     }
    public @Nullable ObservableSet<TaskFx>      predecessorsObservable()  { return predecessors; }
    public @Nullable ObservableSet<TaskFx>      successorsObservable()    { return successors;   }

    public TaskFx(String name, TaskGroupFx taskGroup)
    {
        this.name.set(Objects.requireNonNull(name, "name"));
        Objects.requireNonNull(taskGroup, "taskGroup");
        taskGroup.addTask(this);
    }

    /** Mapping constructor — copies id, version and name from a TaskBean. */
    public TaskFx(TaskGroupFx group, TaskBean in)
    {
        if (in.id() != null) this.id.set(in.id());
        this.version = in.version();
        this.name.set(in.name());
        Objects.requireNonNull(group, "group");
        group.addTask(this);
    }

    /** Package-private — called exclusively by TaskGroupFx.addTask() to avoid recursion. */
    void taskGroupInternal(TaskGroupFx group) { this.taskGroup.set(group); }

    @Override public @Nullable Long    id()              { return id.get() == 0 ? null : id.get(); }
    public    @Nullable Short          version()         { return version;                          }
    @Override public           String  name()            { return name.get();             }
    @Override public           TaskFx  name(String name) { this.name.set(Objects.requireNonNull(name, "name")); return this; }

    @Override
    public Optional<TaskGroupFx> taskGroup() { return Optional.ofNullable(taskGroup.get()); }

    @Override
    public TaskFx taskGroup(TaskGroupFx group)
    {
        if (Objects.requireNonNull(group, "group") != this.taskGroup.get()) group.addTask(this);
        return this;
    }

    @Override
    public Optional<TaskFx> parentTask() { return Optional.ofNullable(parentTask.get()); }

    @Override
    public TaskFx parentTask(@Nullable TaskFx parent) { this.parentTask.set(parent); return this; }

    @Override public Optional<Set<TaskFx>> subTasks()     { return Optional.ofNullable(subTasks);     }
    @Override public Optional<Set<TaskFx>> predecessors() { return Optional.ofNullable(predecessors); }
    @Override public Optional<Set<TaskFx>> successors()   { return Optional.ofNullable(successors);   }

    @Override
    public void addSubTask(TaskFx child)
    {
        Objects.requireNonNull(child, "child");
        if (subTasks == null) subTasks = FXCollections.observableSet(new LinkedHashSet<>());
        if (subTasks.add(child)) child.parentTask(this);
    }

    @Override
    public void removeSubTask(TaskFx child)
    {
        Objects.requireNonNull(child, "child");
        if (subTasks != null && subTasks.remove(child))
            child.parentTask().filter(p -> p == this).ifPresent(p -> child.parentTask(null));
    }

    @Override
    public void addPredecessor(TaskFx predecessor)
    {
        Objects.requireNonNull(predecessor, "predecessor");
        if (predecessors == null) predecessors = FXCollections.observableSet(new LinkedHashSet<>());
        if (predecessors.add(predecessor)) predecessor.addSuccessor(this);
    }

    @Override
    public void removePredecessor(TaskFx predecessor)
    {
        Objects.requireNonNull(predecessor, "predecessor");
        if (predecessors != null && predecessors.remove(predecessor))
            predecessor.removeSuccessor(this);
    }

    @Override
    public void addSuccessor(TaskFx successor)
    {
        Objects.requireNonNull(successor, "successor");
        if (successors == null) successors = FXCollections.observableSet(new LinkedHashSet<>());
        if (successors.add(successor)) successor.addPredecessor(this);
    }

    @Override
    public void removeSuccessor(TaskFx successor)
    {
        Objects.requireNonNull(successor, "successor");
        if (successors != null && successors.remove(successor))
            successor.removePredecessor(this);
    }
}
