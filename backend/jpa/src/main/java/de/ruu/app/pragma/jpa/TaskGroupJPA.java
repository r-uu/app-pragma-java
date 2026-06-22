package de.ruu.app.pragma.jpa;

import de.ruu.app.pragma.core.TaskGroupEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

@Entity
@Table(name = "task_group")
public class TaskGroupJPA implements TaskGroupEntity<TaskJPA>
{
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "task_group_seq")
    @SequenceGenerator(name = "task_group_seq", sequenceName = "task_group_seq", allocationSize = 50)
    private @Nullable Long id;

    @Version
    private @Nullable Short version;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "taskGroup", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private @Nullable Set<TaskJPA> tasks;

    /** JPA-required no-arg constructor. */
    protected TaskGroupJPA() { name = ""; }

    public TaskGroupJPA(String name) { this.name = requireNonNull(name, "name"); }

    @Override public @Nullable Long                   id     () { return id;                }
    @Override public @Nullable Short                  version() { return version;           }
    @Override public           String                 name   () { return name;              }
    @Override public           Optional<Set<TaskJPA>> tasks  () { return ofNullable(tasks); }

    @Override public TaskGroupJPA name(String name) { this.name = requireNonNull(name, "name"); return this; }

    @Override
    public void addTask(TaskJPA task)
    {
        requireNonNull(task, "task");
        if (tasks == null) tasks = new LinkedHashSet<>();
        if (tasks.add(task)) {
            task.taskGroup()
                .filter(old -> old != this)
                .ifPresent(old -> old.tasks().ifPresent(t -> t.remove(task)));
            task.taskGroupInternal(this);
        }
    }

    @Override
    public void removeTask(TaskJPA task)
    {
        requireNonNull(task, "task");
        if (tasks != null) tasks.remove(task);
    }
}
