# Projektkontext: pragma (Java / Jakarta EE Backend & JavaFX Frontend)

## Hinweise für KI-Agenten

Diese Datei dient als Kontext-Datei für AI-Agenten wie Claude Code oder Gemini. Sie soll bei jedem Chat mit AI-Agenten berücksichtigt und automatisch aktuell gehalten werden.

Diese Datei enthält einen [planerischen (Projektziele)](#projektziele) und einen [operativen Teil (Projektumsetzung)](#projektumsetzung).

---

# Projektziele

## Fachliche Projektziele

Das zu entwickelnde Softwaresystem soll dem Anwender dabei helfen, in einem Projekt anfallenden Aufgaben (Tasks) zu strukturieren und den zeitlichen Ablauf für die Umsetzung der Aufgaben zu planen.

### Strukturierung von Aufgaben

Die Strukturierung von Aufgaben soll es ermöglichen, komplexe Aufgaben in kleinere Teilaufgaben zu gliedern. Dieser Strukturierungsschritt soll sich beliebig häufig wiederholen lassen. Dabei entsteht eine Hierarchie von Aufgaben und Teilaufgaben. Diese Hierarchie ist eine Aggregatsbeziehung, bei der die Teile unabhängig von anderen Teilen existieren können.

Es geht um

- das Erreichen von Zielen und
- die Planung und Durchführung von dazu zu erledigenden Aufgaben.

Die Aufgaben werden in Gruppen (TaskGroups) organisiert.

## Technische Projektziele

Das Projekt orientiert sich inhaltlich und technisch an den jeeeraaah-Projekten
[backend](https://github.com/r-uu/main_java) und [frontend](https://github.com/r-uu/main_cmp),
stellt aber einen echten Neuanfang dar.

---

# Projektname

**pragma** — aus dem Griechischen, „die erledigte Tat". Der Wortstamm *prag-* (von *prattein* = tun, planen, handeln) umfasst den ganzen Zyklus: planen → handeln → fertigstellen.

- Package-Prefix: `de.ruu.app.pragma`
- Slogan-Kandidaten: *plan – execute – deliver* / *plan – perform – impact*

---

# Repository-Struktur

| Repo           | Inhalt                                                       |
|----------------|--------------------------------------------------------------|
| `pragma-java`  | Jakarta EE Backend + JavaFX Frontend (dieses Repo)          |
| `pragma-cmp`   | Kotlin Compose Multiplatform Frontend                        |
| `ruu-java-lib` | Allgemeine Java-Bibliothek inkl. BOM-Modul (projektübergreifend) |

Das BOM-Modul lebt als Maven-Submodul in `ruu-java-lib` und verwaltet gemeinsame Dependency-Versionen für alle Projekte (pragma, jeeeraaah, weitere). Nicht alle Dependencies werden im BOM verwaltet — nur die projektübergreifend relevanten.

---

# Projektumsetzung

## Technologie-Stack

- **Backend:** Java, Jakarta EE, JPA, JAX-RS / JAX-WS
- **Frontend:** JavaFX (in diesem Repo), Kotlin CMP (in `pragma-cmp`)
- **Build:** Maven (mit BOM aus `ruu-java-lib`)

## Schichtenmodell

Tasks durchlaufen mehrere Schichten, die sich möglichst analog verhalten sollen:

| Schicht   | Klasse         | Beschreibung                              |
|-----------|----------------|-------------------------------------------|
| Bean/POJO | `TaskBean`     | Einfaches Java-Objekt ohne Framework      |
| JPA       | `TaskEntity`   | JPA-Entity mit Annotationen               |
| DTO       | `TaskDto`      | Datentransferobjekt für REST/WS           |
| JavaFX    | `TaskFx`       | Observable-Objekt mit JavaFX Properties   |

## Interface-Hierarchie für Task

Das geteilte Verhalten aller Schichten wird durch generische Interfaces erzwungen.
Attributnamen: `parentTask` (Elterntask) und `subTasks` (Kindtasks).

```java
// de.ruu.app.pragma.core

public interface HasId<ID> {
    ID getId();
}

public interface HasTitle {
    String getTitle();
    void setTitle(String title);
}

public interface HasParentTask<T> {
    T getParentTask();
    void setParentTask(T parentTask);
}

public interface HasSubTasks<T> {
    List<T> getSubTasks();
}

// Kern-Interface: gilt für Bean, JPA-Entity und DTO
public interface Task<T extends Task<T>>
        extends HasId<Long>, HasTitle, HasParentTask<T>, HasSubTasks<T> {
}
```

```java
// de.ruu.app.pragma.fx

public interface ObservableTask<T extends ObservableTask<T>> extends Task<T> {
    Property<T> parentTaskProperty();
    @Override
    ObservableList<T> getSubTasks(); // kovariant – ObservableList ist ein List
}
```

### Implementierungen

```java
// Bean
public class TaskBean implements Task<TaskBean> { ... }

// JPA
@Entity
public class TaskEntity implements Task<TaskEntity> {
    @Id @GeneratedValue
    private Long id;
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private TaskEntity parentTask;

    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL)
    private List<TaskEntity> subTasks = new ArrayList<>();
}

// DTO
public class TaskDto implements Task<TaskDto> { ... }

// JavaFX
public class TaskFx implements ObservableTask<TaskFx> {
    private final LongProperty              id         = new SimpleLongProperty();
    private final StringProperty            title      = new SimpleStringProperty();
    private final ObjectProperty<TaskFx>    parentTask = new SimpleObjectProperty<>();
    private final ObservableList<TaskFx>    subTasks   = FXCollections.observableArrayList();

    @Override public ObjectProperty<TaskFx> parentTaskProperty() { return parentTask; }
    @Override public ObservableList<TaskFx> getSubTasks()        { return subTasks;   }
}
```

### Trade-offs

**Vorteil:** Generische Utilities (z. B. Baumtraversierung) funktionieren schichtenübergreifend:
```java
<T extends Task<T>> List<T> collectLeaves(T root) { ... }
```

**Nachteil:** Die F-bounded Generik (`T extends Task<T>`) erschwert schichtenübergreifende
Collections. Wenn `List<Task<?>>` über Schichtgrenzen benötigt wird, kann ein gemeinsamer
nicht-generischer Basistyp (`RawTask`) ergänzt werden.
