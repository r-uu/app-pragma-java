# Objektmodell: Implementierungsentscheidungen

## Überblick

Das Objektmodell besteht aus zwei zentralen Domänenobjekten: `Task` und `TaskGroup`.
Beide existieren in jeder Schicht des Systems (Bean, JPA, DTO, JavaFX) als schichtspezifische
Implementierungen desselben generischen Interfaces.

---

## Interface-Hierarchie

### Trennung von lesend und mutierend

Attribute existieren in zwei Interface-Varianten:

| Nur-lesen        | Mutable (extends lesen) |
|------------------|-------------------------|
| `HasTitle`       | `HasMutableTitle`       |
| `HasParentTask`  | `HasMutableParentTask`  |
| `HasTaskGroup`   | `HasMutableTaskGroup`   |

`HasId`, `HasSubTasks`, `HasPredecessors`, `HasSuccessors` existieren nur in der lesenden Form,
da diese Attribute entweder durch den Persistenz-Layer verwaltet werden (`id`) oder
ausschließlich über die Liste direkt mutiert werden (Collections).

### Fluent Style

Methoden verwenden kein `get`/`set`-Präfix. Getter und Setter tragen denselben Namen wie das
Attribut; Java unterscheidet sie durch die unterschiedliche Signatur (ohne vs. mit Argument).
Setter geben den Interface-Typ (bzw. in Implementierungen den konkreten Typ) zurück, um
Method-Chaining zu ermöglichen. Implementierungen verwenden kovariante Rückgabetypen.

```java
// Interfaces
String title();                       // Getter
HasMutableTitle title(String title);  // Setter

// Implementierung mit kovarianten Rückgabetypen
@Override public String    title()               { return title;                  }
@Override public TaskBean  title(String title)   { this.title = title; return this; }

// Verwendung
TaskBean task = new TaskBean("initial").title("Analyse").taskGroup(group);
```

**Hinweis zu JPA:** JPA-Entities müssen JavaBeans-Konventionen (`getX`/`setX`) für
Property-Access-Mode einhalten. Da dieses Projekt Field-Access verwendet (`@Id` auf dem Feld,
nicht auf dem Getter), ist das kein Problem — fluent-style Methoden und JPA-Field-Access sind
kompatibel.

### Raw-Interfaces

`RawTask` und `RawTaskGroup` kombinieren `HasId<Long>` und `HasTitle` ohne Generik.
Sie dienen als gemeinsamer Basistyp für schichtenübergreifende Referenzen,
wo die F-bounded Generik von `Task<T>` nicht handhabbar ist.

Konkreter Anwendungsfall: `HasTaskGroup` und `HasMutableTaskGroup` referenzieren `RawTaskGroup`
statt `TaskGroup<G>`, damit `TaskBean` eine `TaskGroupBean` halten kann, ohne dass
`TaskBean` selbst generisch über `G` parametrisiert werden muss.

### Task-Interface

```
HasId<Long>
HasTitle  <──  HasMutableTitle
HasParentTask<T>  <──  HasMutableParentTask<T>
HasSubTasks<T>
HasPredecessors<T>
HasSuccessors<T>
HasTaskGroup  <──  HasMutableTaskGroup
     │
     └─ RawTask (HasId<Long> + HasTitle)
              │
              └─ Task<T extends Task<T>>
                      extends RawTask,
                              HasMutableTitle,
                              HasMutableParentTask<T>,
                              HasSubTasks<T>,
                              HasPredecessors<T>,
                              HasSuccessors<T>,
                              HasMutableTaskGroup
```

### TaskGroup-Interface

```
RawTaskGroup (HasId<Long> + HasTitle)
      │
      └─ TaskGroup<G extends TaskGroup<G>>
               extends RawTaskGroup, HasMutableTitle
```

`TaskGroup` ist bewusst schlank gehalten: sie hat keinen eigenen Verweis auf ihre Tasks,
weil die Zuordnung auf der Task-Seite über `HasMutableTaskGroup` geführt wird.

---

## F-bounded Generik

`Task<T extends Task<T>>` und `TaskGroup<G extends TaskGroup<G>>` ermöglichen
schichtenübergreifend typsichere Utilities:

```java
<T extends Task<T>> List<T> collectLeaves(T root) { ... }
```

Der Typ-Parameter erzwingt, dass `getParentTask()`, `getSubTasks()`, `getPredecessors()` und
`getSuccessors()` immer den konkreten Typ der jeweiligen Schicht zurückgeben —
keine unsicheren Casts nötig.

Nachteil: heterogene `List<Task<?>>` über Schichtgrenzen sind umständlich.
Dafür existiert `RawTask` als Ausweg (siehe oben).

---

## Optional für nullable Referenzen

`getParentTask()` gibt `Optional<T>` zurück, `getTaskGroup()` gibt `Optional<RawTaskGroup>`.
Root-Tasks haben keinen Parent, Tasks können ohne Gruppe existieren — beides sind
fachlich legitime Zustände, keine Fehler. `Optional` macht diese Semantik im Typ sichtbar
und verhindert unbewusste NPE-Fallen in Konsumenten.

`setParentTask(T parentTask)` akzeptiert dagegen `T` (nicht `Optional<T>`), weil
ein Setter mit `null` das Löschen des Parents ausdrückt — eine übliche Java-Konvention
für JPA-kompatible Setter.

---

## Predecessor / Successor als explizite Listen

Die Vorgänger-/Nachfolger-Beziehung wird als zwei separate Listen modelliert
(`HasPredecessors<T>` und `HasSuccessors<T>`), nicht als einzelne gerichtete Kante.

Begründung: Beide Richtungen werden häufig unabhängig abgefragt (z. B. "welche Tasks
blockieren diesen?" vs. "welche Tasks werden durch diesen freigeschaltet?"). Eine einzige
gerichtete Kante würde immer einen vollständigen Graph-Scan erfordern.

Die Konsistenz beider Listen (wenn A als Predecessor von B gilt, muss B als Successor von A
geführt werden) ist Aufgabe der Implementierung, nicht des Interfaces.
Das Interface erzwingt dies bewusst nicht, um unterschiedliche Implementierungen
(lazy JPA, In-Memory-Bean) nicht einzuschränken.

---

## Bean-Schicht

`TaskBean` ist ein reines POJO ohne Framework-Abhängigkeiten. Es dient als:
- Testgrundlage ohne JPA-Kontext
- Referenzimplementierung für das `Task<T>`-Interface
- Basis für Unit-Tests der Business-Logik

Die `id` in `TaskBean` hat keinen Setter und wird nicht im Konstruktor gesetzt —
in der Bean-Schicht ist sie optional und typischerweise `null`.

`TaskGroupBean` ist entsprechend schlank: nur `id` und `title`.

---

## Offene Entscheidungen (TODOs)

- **Flat DTOs**: Brauchen wir DTOs, die nur skalare Felder (keine Relationen) enthalten?
- **Relation-DTOs**: Brauchen wir DTOs, die ausschließlich Relationen abbilden?
- **Id in allen Schichten**: Soll `id` in Bean und DTO immer gesetzt sein (vgl. jeeeraaah-Projekt)?
- **mapstruct**: Bewertung steht aus — bisheriger Eindruck aus dem Altprojekt war negativ.
