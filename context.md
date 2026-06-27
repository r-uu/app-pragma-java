# Projektkontext: pragma (Java / Jakarta EE Backend & JavaFX Frontend)

## Hinweise für KI-Agenten

Diese Datei dient als Kontext-Datei für AI-Agenten wie Claude Code oder Gemini. Sie soll bei jedem Chat mit AI-Agenten berücksichtigt und automatisch aktuell gehalten werden.

[Übergeordnete Kontext-Datei](../app-lib-java/context.md) immer zuerst lesen, wird durch diese Datei ggf. überschrieben.

Diese Datei enthält einen [planerischen (Projektziele)](#projektziele) und einen [operativen Teil (Projektumsetzung)](#projektumsetzung).

---

# Projektziele

## Fachliche Projektziele

Das zu entwickelnde Softwaresystem soll dem Anwender dabei helfen, in einem Projekt anfallenden Aufgaben (Task) zu strukturieren und den zeitlichen Ablauf für die Umsetzung der Aufgaben zu planen. Task können dabei in Taskgruppen (TaskGroup) organisiert werden.

### Strukturierung von Tasks

Im Projekt geht es um

- das Erreichen von Zielen und
- die Planung und Durchführung von dazu zu erledigenden Aufgaben.

Aufgaben können dazu nach unterschiedlichen Kategorien organisiert werden:

- [Task-Gruppen](#taskgruppen),
- [Aggregationen von Tasks](#aggregation-von-tasks) und
- [Abfolgen von Tasks](#abfolge-von-tasks).

#### Taskgruppen

Tasks können in Gruppen organisiert werden.

#### Aggregation von Tasks

Die Strukturierung von Aufgaben soll es ermöglichen, komplexe Aufgaben in kleinere Teilaufgaben zu gliedern. Dieser Strukturierungsschritt soll sich beliebig häufig wiederholen lassen. Dabei entsteht eine Hierarchie von Aufgaben und Teilaufgaben. Diese Hierarchie ist eine Aggregatsbeziehung, bei der die Teile unabhängig von anderen Teilen existieren können.

#### Abfolge von Tasks

Tasks können in Vorgänger-/Nachfolgerbeziehungen (predecessor-/successor-Beziehungen) gesetzt werden.

Theoretisch sollte eine "physische" Relation zwischen Task-Objekten dazu ausreichend sein.

#### Regeln für die Strukturierung von Tasks

- Technisch gesehen handelt es sich bei den genannten Relationen jeweils um Aggregatsbeziehungen. D. h. Tasks existieren im Unterschied zur Komposition unabhängig von ihren Beziehungen.
- Fachlich gesehen sind bestimmte Konstellationen verboten: weder in der Aggregations- noch in der Abfolge-Beziehung dürfen aus fachlicher Sicht Zirkel entstehen. In der Praxis kann das aber passieren, wenn dem Anwender der Überblick über bestehenden Beziehungen verloren geht. Das System lässt solche Beziehungen zu, bietet aber optional Funktionalität um solche Zirkel aufzulösen.

## Technische Projektziele

Das Projekt orientiert sich inhaltlich und technisch an den pragma-Projekten [backend](https://github.com/r-uu/main_java) und [frontend](https://github.com/r-uu/main_cmp), stellt aber einen echten Neuanfang dar.

### mapstruct

Im alten Backend hat sich mapstruct nicht als sehr hilfreich erwiesen. In diesem Projekt soll mapstruct auf den Prüfstand gestellt werden. Ein Verzicht auf mapstruct ist eine valide Option. AI-Agenten sollen dazu eine sinnvolle Einschätzung abgeben.

---

# Projektumsetzung

## Technologie-Stack

- **Backend:** Java 25, Jakarta EE 10, JPA (Hibernate), JAX-RS (Jersey auf dem Client)
- **Server:** Open Liberty 25.0.0.12, Datasource PostgreSQL via JDBC
- **Frontend:** JavaFX (in diesem Repo), Kotlin CMP (in `pragma-cmp`)
- **Build:** Maven (mit BOM aus `ruu-java-lib`)
- **JSON:** Jackson mit Field-Visibility (FIELD=ANY, GETTER/SETTER/IS_GETTER=NONE)

## Schichtenmodell

Tasks (und TaskGroups) durchlaufen mehrere Schichten, die sich möglichst analog verhalten sollen:

| Schicht   | Klasse          | Beschreibung                                      |
|-----------|-----------------|---------------------------------------------------|
| Bean/POJO | `TaskBean`      | Einfaches Java-Objekt ohne Framework              |
| JPA       | `TaskJPA`       | JPA-Entity mit Annotationen                       |
| DTO       | `TaskDto`       | Datentransferobjekt für REST                      |
| JavaFX    | `TaskFx`        | Observable-Objekt mit JavaFX Properties (geplant) |

## Namenskonventionen

- Methoden ohne `get`/`set`-Präfix: `id()`, `name()`, `parentTask()` usw.
- Fluent-Setter geben `this` zurück (kovariant: Interface-Typ im Interface, konkreter Typ in Implementierungen).
- Fluent-Setter für Singular-Relationen (`parentTask`, `taskGroup`) geben den Self-Type `T` zurück, da `T` als Typparameter verfügbar ist.
- Mutations-Methoden für Collections (`addSubTask`, `removePredecessor` …) geben `void` zurück.

## Optional-Semantik für Relationen

**To-one-Relationen** (`parentTask`, `taskGroup`) — JPA-Default: EAGER:
- `Optional.empty()` = kein Wert (Domänenfakt: Task ist Root-Task bzw. keiner Gruppe zugeordnet)
- `Optional.of(t)` = hat Wert

**To-many-Relationen** (`subTasks`, `predecessors`, `successors`, `tasks`) — JPA-Default: LAZY:
- `Optional.empty()` = noch nicht geladen (Infrastrukturfakt, null intern)
- `Optional.of(emptySet)` = geladen, leer
- `Optional.of(set)` = geladen, hat Elemente

Die Unterscheidung ist durch die Kardinalität der Relation eindeutig — keine zusätzliche Dokumentation am Aufrufpunkt nötig.

## Bidirektionale Relationen

Alle bidirektionalen Relationen werden durch explizite add/remove-Methoden verwaltet.
Die Methoden pflegen beide Seiten der Relation und sind gegen unendliche Rekursion gesichert
(`Set.add()` gibt `false` zurück, wenn das Element bereits vorhanden ist).

| Relation                  | Methoden                                    | gegenseitige Pflege                  |
|---------------------------|---------------------------------------------|--------------------------------------|
| parentTask / subTasks     | `addSubTask(T)`, `removeSubTask(T)`         | setzt parentTask auf der Kindseite   |
| predecessors / successors | `addPredecessor(T)`, `removePredecessor(T)` | registriert Successor auf Gegenseite |
| taskGroup / tasks         | `addTask(T)`, `removeTask(T)`               | setzt taskGroup auf der Task-Seite   |

## Interface-Hierarchie

Das geteilte Verhalten aller Schichten wird durch generische Interfaces erzwungen.

```java
// de.ruu.app.pragma.core — Basisinterfaces

public interface HasId<ID>    { ID     id();   }
public interface HasName      { String name(); }
public interface HasMutableName extends HasName
                              { HasMutableName name(String name); }

// T = Self-Type → Setter gibt T zurück, kein Cast nötig
public interface HasParentTask<T>        { Optional<T> parentTask();   }
public interface HasMutableParentTask<T>
        extends HasParentTask<T>         { T parentTask(T t);          }

// Optional<Set<T>>: empty = not loaded, of(set) = loaded
public interface HasSubTasks<T>          { Optional<Set<T>> subTasks();      }
public interface HasPredecessors<T>      { Optional<Set<T>> predecessors();  }
public interface HasSuccessors<T>        { Optional<Set<T>> successors();    }

public interface HasMutableSubTasks<T>   extends HasSubTasks<T>
                              { void addSubTask(T t);    void removeSubTask(T t);    }
public interface HasMutablePredecessors<T> extends HasPredecessors<T>
                              { void addPredecessor(T t); void removePredecessor(T t); }
public interface HasMutableSuccessors<T> extends HasSuccessors<T>
                              { void addSuccessor(T t);  void removeSuccessor(T t);  }

public interface HasTaskGroup<G extends TaskGroup<?>>
                              { Optional<G> taskGroup(); }
public interface HasMutableTaskGroup<G extends TaskGroup<?>>
        extends HasTaskGroup<G> { HasMutableTaskGroup<G> taskGroup(G g); }

// Nicht-generische Anker für schichtenübergreifende Collections
public interface RawTask      extends HasId<Long>, HasName {}
public interface RawTaskGroup extends HasId<Long>, HasName {}

// G = TaskGroup-Typ, T = eigener Typ (F-Bound)
// Wildcard im G-Bound nötig wegen bidirektionaler TaskGroup.tasks()-Referenz
public interface Task<G extends TaskGroup<? extends Task<G, ?>>, T extends Task<G, T>>
        extends RawTask, HasMutableName, HasMutableParentTask<T>,
                HasMutableSubTasks<T>, HasMutablePredecessors<T>, HasMutableSuccessors<T>,
                HasMutableTaskGroup<G> {}

public interface TaskGroup<T extends Task<?, ?>> extends RawTaskGroup, HasMutableName
{
    Optional<Set<T>> tasks();     // empty = not loaded
    void addTask(T task);
    void removeTask(T task);
}

// Persistence-Schicht: ergänzt version() für JPA @Version
public interface TaskEntity
        <G extends TaskGroupEntity<? extends TaskEntity<G, ?>>, T extends TaskEntity<G, T>>
        extends Task<G, T>      { Long version(); }

public interface TaskGroupEntity<T extends TaskEntity<?, ?>>
        extends TaskGroup<T>    { Long version(); }
```

### Implementierungen

| Schicht   | Task-Klasse     | TaskGroup-Klasse     | Modul                |
|-----------|-----------------|----------------------|----------------------|
| Bean/POJO | `TaskBean`      | `TaskGroupBean`      | `bean`               |
| JPA       | `TaskJPA`       | `TaskGroupJPA`       | `backend/jpa`        |
| DTO       | `TaskDto`       | `TaskGroupDto`       | `backend/dto`        |
| JavaFX    | `TaskFx`        | `TaskGroupFx`        | `frontend/fx` (geplant) |

### Trade-offs

**Vorteil:** Generische Utilities (z. B. Baumtraversierung) funktionieren schichtenübergreifend:
```java
<G extends TaskGroup<? extends Task<G, ?>>, T extends Task<G, T>>
Set<T> collectLeaves(T root) { ... }
```

**Wildcard-Komplexität** entsteht zwingend durch die bidirektionale Referenz Task ↔ TaskGroup
(wie im jeeeraaah-Projekt). `RawTask`/`RawTaskGroup` dienen als Anker für `List<RawTask>`
über Schichtgrenzen ohne Wildcard.

---

# Projektumsetzung — aktueller Stand

## Repository-Struktur

| Repo           | Inhalt                                                           |
|----------------|------------------------------------------------------------------|
| `pragma-java`  | Jakarta EE Backend + JavaFX Frontend (dieses Repo)               |
| `pragma-cmp`   | Kotlin Compose Multiplatform Frontend                            |
| `ruu-java-lib` | Allgemeine Java-Bibliothek inkl. BOM-Modul (projektübergreifend) |

Das BOM-Modul lebt als Maven-Submodul in `ruu-java-lib` und verwaltet gemeinsame Dependency-Versionen für alle Projekte (pragma, pragma, weitere). Nicht alle Dependencies werden im BOM verwaltet — nur die projektübergreifend relevanten.

## Maven-Modulstruktur

```
app-pragma-java/
├── pom.xml                       (r-uu.app.pragma, parent: r-uu.bom aus lib-java)
├── core/                         (r-uu.app.pragma.core)          — Interfaces (Task, TaskGroup, Has*)
├── bean/                         (r-uu.app.pragma.bean)          — POJO-Implementierung
├── backend/
│   ├── pom.xml                   (r-uu.app.pragma.backend)
│   ├── dto/                      (r-uu.app.pragma.dto)           — DTOs für REST (Jackson)
│   ├── jpa/                      (r-uu.app.pragma.jpa)           — JPA-Entities (Hibernate)
│   └── rest/                     (r-uu.app.pragma.rest, WAR)     — JAX-RS REST-API + Liberty Server
│       └── src/main/liberty/config/
│           ├── server.xml        — Liberty-Konfiguration (Port 9090, Datasource jdbc/pragma)
│           └── server.env        — Umgebungsvariablen (Ports, DB-Credentials)
└── frontend/
    ├── pom.xml                   (r-uu.app.pragma.frontend)
    ├── rest-client/              (r-uu.app.pragma.rest-client)   — JAX-RS Client (Jersey)
    │   └── src/main/java/de/ruu/app/pragma/client/
    │       ├── TaskGroupClient.java
    │       ├── TaskClient.java
    │       └── dbcommand/        — DB-Hilfsprogramme (clear/populate)
    └── fx/                       (r-uu.app.pragma.fx, geplant)   — JavaFX UI
```

## Open Liberty Server

- **Port:** 9090 (HTTP), 9543 (HTTPS) — um Konflikt mit jeeeraaah (9080) zu vermeiden
- **Context root:** `/pragma`, JAX-RS base: `/pragma/api`
- **Features:** `jakartaee-10.0`, `microProfile-6.1`
- **Datasource:** `jdbc/pragma` → PostgreSQL (Container `pragma-postgres`, Port 5432)
- **JPA provider:** Hibernate (JARs in `lib/global/`, nicht im WAR gebündelt)
- **Start:** `mvn -pl backend/rest liberty:run` (Produktion) oder `mvn -pl backend/rest liberty:dev` (Hot-Reload)

## Jackson-Konfiguration

Jackson ist als primärer JSON-Provider registriert (überschreibt Liberty's Yasson/JSON-B):

```java
// PragmaApplication.getSingletons()
ObjectMapper mapper = new ObjectMapper()
    .registerModule(new Jdk8Module())
    .registerModule(new JavaTimeModule())
    .setVisibility(...withFieldVisibility(ANY).withGetterVisibility(NONE)...)
```

DTOs benötigen:
- Protected no-arg Konstruktor (für Jackson-Deserialisierung)
- `opens de.ruu.app.pragma.dto;` in `module-info.java` (für reflektiven Feldzugriff)

## REST-Endpoints

Basis-URL: `http://localhost:9090/pragma/api`

| Methode  | Pfad                               | Beschreibung                                       |
|----------|------------------------------------|----------------------------------------------------|
| GET      | `/task-groups`                     | Alle Gruppen                                       |
| GET      | `/task-groups/{id}`                | Gruppe nach ID                                     |
| POST     | `/task-groups`                     | Neue Gruppe                                        |
| PUT      | `/task-groups/{id}`                | Gruppe umbenennen                                  |
| DELETE   | `/task-groups/{id}`                | Gruppe löschen                                     |
| DELETE   | `/task-groups`                     | Alle Gruppen + Tasks löschen (inkl. Join-Tabellen) |
| GET      | `/tasks?groupId=`                  | Tasks (optional gefiltert nach Gruppe)             |
| GET      | `/tasks/{id}`                      | Task nach ID                                       |
| GET      | `/tasks/{id}/with-related`         | Task mit subTasks, predecessors, successors (eager)|
| GET      | `/tasks/group/{groupId}/with-related` | Alle Tasks einer Gruppe mit Relationen (eager)  |
| POST     | `/tasks`                           | Neuer Task                                         |
| PUT      | `/tasks/{id}`                      | Task umbenennen                                    |
| PUT      | `/tasks/{id}/group/{groupId}`      | Task in andere Gruppe verschieben                  |
| PUT      | `/tasks/{id}/parent/{parentId}`    | Eltern-Task setzen                                 |
| DELETE   | `/tasks/{id}/parent`               | Eltern-Task aufheben                               |
| PUT      | `/tasks/{id}/predecessor/{predId}` | Vorgänger hinzufügen                               |
| DELETE   | `/tasks/{id}/predecessor/{predId}` | Vorgänger entfernen                                |
| DELETE   | `/tasks/{id}`                      | Task löschen                                       |

## JPMS-Entscheidungen

- **Named modules** (mit `module-info.java`): `core`, `bean`, `dto`, `jpa`, `rest-client` (Modulname: `de.ruu.app.pragma.client`), `fx`
  - Hyphene sind in JPMS-Modulnamen verboten — `rest-client` heißt deshalb `de.ruu.app.pragma.client`
  - Jersey (`jersey.client`) und MicroProfile Config (`microprofile.config.api`) sind automatische Module (Modulname aus JAR-Dateiname abgeleitet)
- **Unnamed modules** (ohne `module-info.java`): `backend/rest` (WAR)
  - Grund: Liberty WAR-Deployment nutzt eigenes Classloading, JPMS nicht sinnvoll

## Hinweise

- Das BOM (`r-uu.bom:0.0.1`) muss vor dem Build dieses Projekts im lokalen Maven-Repository vorhanden sein (`mvn install` in `lib-java`).
- `backend/rest/src/main/liberty/config/lib/global/` ist gitignored — Hibernate-JARs werden beim Build durch `maven-dependency-plugin` dorthin kopiert.
- `docker-compose.yml` im Projektroot startet PostgreSQL 17 (Container `pragma-postgres`).

# Planung

## JavaFX Frontend um JavaFXSmartGraph erweitern

Empfehlung: Setze auf JavaFXSmartGraph. Es spart dir bei der Implementierung von komplexen Netzbeziehungen (wie deinen Predecessor-/Successor-Abfolgen) Wochen an Arbeit, die du bei FXGraph in das Schreiben eigener Layout-Algorithmen stecken müsstest. Nimm dazu die Bibliothek JGraphT für die mathematische Zyklerkennung im Hintergrund, füttere das Ergebnis in JavaFXSmartGraph ein, und du hast eine extrem robuste und moderne UI-Erweiterung für deinen Client.

## Aufbau der FX UI

Die pragma-UI soll sich an der jeeeraaah-UI orientieren. Sie soll insbesondere das FXC-Framework aus lib-java verwenden. Einstieg in die pragma-UI soll eine FXC-App sein, die zunächst folgende Kacheln anzeigt:

- Hierarchy View: zeigt FXCView Hierarchies (orientiert sich an de.ruu.app.jeeeraaah.frontend.ui.fx.task.hierarchy.Hierarchies)
- Gantt View:     zeigt FXCView Gantt       (orientiert sich an de.ruu.app.jeeeraaah.frontend.ui.fx.task.gantt.Gantt)
- Graph View:     zeigt FXCView Graph, wird mit JavaFXSmartGraph neu erstellt

Die Views sollen analog zur jeeeraaah Version autonom als FXCApp lauffähig sein.

### Aktueller Stand (FX UI)

Alle drei FXC-Views sowie die Haupt-App sind implementiert in `frontend/fx`:

#### Hierarchies View (`de.ruu.app.pragma.fx.task.hierarchy`)
| Klasse                  | Rolle                                                                 |
|-------------------------|-----------------------------------------------------------------------|
| `HierarchiesService`    | FXCService-Interface                                                  |
| `Hierarchies`           | FXCView — lädt `Hierarchies.fxml`                                     |
| `HierarchiesController` | 3-Panel-Layout: predecessor ← task → successor (TreeViews, RTL links) |
| `HierarchiesApp`        | FXCApp standalone                                                     |
| `HierarchiesAppRunner`  | `main()` entry point                                                  |

#### Gantt View (`de.ruu.app.pragma.fx.task.gantt`)
| Klasse              | Rolle                                                                    |
|---------------------|--------------------------------------------------------------------------|
| `GanttService`      | FXCService-Interface                                                     |
| `Gantt`             | FXCView — lädt `Gantt.fxml`                                              |
| `GanttController`   | TreeTableView: Namensspalte + Tagesspalten (blau = planned); Datumeditor |
| `GanttApp`          | FXCApp standalone                                                        |
| `GanttAppRunner`    | `main()` entry point                                                     |

Voraussetzung: `TaskDto`/`TaskJPA` haben `plannedStart`/`plannedEnd` (LocalDate, nullable).
REST-PUT `/tasks/{id}` persistiert diese Felder. `hbm2ddl.auto=update` fügt Spalten automatisch hinzu.

#### Graph View (`de.ruu.app.pragma.fx.task.graph`)
| Klasse              | Rolle                                                                              |
|---------------------|------------------------------------------------------------------------------------|
| `GraphService`      | FXCService-Interface                                                               |
| `Graph`             | FXCView — lädt `Graph.fxml`                                                        |
| `GraphController`   | Reines JavaFX-Graph: TaskBean-Knoten als abgerundete Rechtecke (Rectangle + VBox), |
|                     | Kanten als Line+Polygon, einmaliges topologisches Auto-Layout, dann nur Dragging.  |
|                     | Ein REST-Call (`findGroupTasksWithRelated`) lädt alle Tasks mit Relationen.         |
| `GraphApp`          | FXCApp standalone                                                                  |
| `GraphAppRunner`    | `main()` entry point                                                               |

Kein SmartGraph für das visuelle Rendering (SmartGraph 2.0.0 unterstützt keine Rechtecke).
SmartGraph-Dependency bleibt im Classpath, wird aber nicht mehr aktiv genutzt.

#### Haupt-App (`de.ruu.app.pragma.fx`)
| Klasse              | Rolle                                |
|---------------------|--------------------------------------|
| `PragmaService`     | FXCService-Interface                 |
| `Pragma`            | FXCView — lädt `Pragma.fxml`         |
| `PragmaController`  | TabPane mit 3 Tabs: Hierarchies      |
| `PragmaApp`         | FXCApp — Haupteinstiegspunkt         |
| `PragmaAppRunner`   | `main()` — startet Weld CDI + JavaFX |

**IntelliJ Run Configs (exec-maven-plugin executions):**
- `pragma fx:hierarchies` → `HierarchiesAppRunner`
- `pragma fx:gantt`       → `GanttAppRunner`
- `pragma fx:graph`       → `GraphAppRunner`
- `pragma fx:pragma`      → `PragmaAppRunner` (Haupt-App mit allen 3 Views)
