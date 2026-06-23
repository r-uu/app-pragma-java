package de.ruu.app.pragma.fx;

import de.ruu.app.pragma.bean.TaskGroupBean;
import de.ruu.app.pragma.core.TaskGroup;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TaskGroupFx implements TaskGroup<TaskFx>
{
    private final LongProperty              id      = new SimpleLongProperty();
    private @Nullable Short                 version;
    private final StringProperty            name    = new SimpleStringProperty();
    private @Nullable ObservableSet<TaskFx> tasks; // null = not yet loaded

    public LongProperty   idProperty()   { return id;   }
    public StringProperty nameProperty() { return name; }
    public @Nullable ObservableSet<TaskFx> tasksObservable() { return tasks; }

    public TaskGroupFx(String name) { this.name.set(Objects.requireNonNull(name, "name")); }

    /** Mapping constructor — copies id, version and name from a TaskGroupBean. */
    public TaskGroupFx(TaskGroupBean in)
    {
        if (in.id() != null) this.id.set(in.id());
        this.version = in.version();
        this.name.set(in.name());
    }

    @Override public @Nullable Long         id()              { return id.get() == 0 ? null : id.get(); }
    public    @Nullable Short               version()         { return version;                          }
    @Override public           String       name()            { return name.get();                       }
    @Override public           TaskGroupFx  name(String name) { this.name.set(Objects.requireNonNull(name, "name")); return this; }
    @Override public           Optional<Set<TaskFx>> tasks()  { return Optional.ofNullable(tasks); }

    @Override
    public void addTask(TaskFx task)
    {
        Objects.requireNonNull(task, "task");
        if (tasks == null) tasks = FXCollections.observableSet(new LinkedHashSet<>());
        if (tasks.add(task)) {
            // null during construction — see HasTaskGroup javadoc
            TaskGroupFx old = task.taskGroup();
            if (old != null && old != this) old.tasks().ifPresent(t -> t.remove(task));
            task.taskGroupInternal(this);
        }
    }

    @Override
    public void removeTask(TaskFx task)
    {
        Objects.requireNonNull(task, "task");
        if (tasks != null) tasks.remove(task);
    }
}
