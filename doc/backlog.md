# Backlog — Schwachstellen und offene Punkte

Analysiert: 2026-06-26. Bezieht sich auf den aktuellen Stand der `main`-Branch.

Priorität orientiert sich daran, was einen produktiven Einsatz **blockiert** (P1),
**stark einschränkt** (P2) oder **mittelfristig wichtig** ist (P3).
Fehlende Domänenfunktionen und strategische Themen sind P4.

---

## P1 — Produktionsblocker (vor erstem echten Einsatz beheben)

### P1-1 · `hbm2ddl.auto=update` ersetzen

**Datei:** `backend/rest/src/main/resources/META-INF/persistence.xml`

`update` erkennt keine Spalten-Drops, Umbenennungen oder komplexe Schemaänderungen und
kann bei ungünstigen Migrationen Daten zerstören.

**Lösung:** Flyway oder Liquibase einführen; `hbm2ddl.auto` auf `validate` setzen.
Migrationsscripte versioniert in `backend/rest/src/main/resources/db/migration/` ablegen.

**Vorgehen:** Da das Daten-/Objektmodell noch nicht final ist, ist update mode erstmal ok.
Mit Datenverlust kann ich aktuell gut leben, vor allem wenn db backup / restore sauber im
pragma-ui integriert sind.

---

### ~~P1-2 · Optimistic Locking ist konfiguriert aber wirkungslos~~ ✅ umgesetzt 2026-06-27

`update()` in `TaskResource` und `TaskGroupResource` prüft jetzt `entity.version()` gegen
`dto.version()` und wirft 409 bei Abweichung. `bean/Mappings.toDto()` propagiert id und
version, damit Clients die Version korrekt mitsenden.

---

### ~~P1-3 · `deleteAll()` ungeschützt und mit falschem Tabellennamen~~ ✅ umgesetzt 2026-06-27

`DELETE /task-groups` vollständig entfernt. `DBClear` löscht jetzt erst alle
Predecessor-Relationen (via REST), dann alle Groups (Cascade auf Tasks). 

---

### ~~P1-4 · Keine Input-Validierung auf REST-Ebene~~ ✅ umgesetzt 2026-06-27

`@NotBlank` auf name-Felder in `TaskDto`, `TaskGroupDto`; `@NotNull` auf groupId in
`TaskCreateRequest`; `@Valid` auf alle POST/PUT-Parameter. `jakarta.validation-api` als
provided-Dependency in dto und rest; `requires jakarta.validation` in dto-module-info.
`ConstraintViolationExceptionMapper` liefert 400 mit Violation-Details.

---

### ~~P1-5 · Kein `ExceptionMapper` — interne Fehler als 500 durchgereicht~~ ✅ umgesetzt 2026-06-27

`GenericExceptionMapper` (500, lässt `WebApplicationException` durch) und
`ConstraintViolationExceptionMapper` (400) als `@Provider` hinzugefügt.

---

## P2 — Starke Einschränkungen im Alltag

### ~~P2-1 · Kein Paging bei `findAll()`~~ ✅ umgesetzt 2026-06-27

`@QueryParam("page")` / `@QueryParam("size")` in `TaskResource` und `TaskGroupResource`
ergänzt; ohne Parameter wird alles geladen (Rückwärtskompatibilität). Clients können
optional page/size übergeben.

---

### ~~P2-2 · Rekursive REST-Calls in `HierarchiesController`~~ ✅ umgesetzt 2026-06-27

`loadGroup()` nutzt jetzt `findGroupTasksWithRelated()` und befüllt `taskByIdCache`.
`buildPredecessorNode()` / `buildSuccessorNode()` traversieren den Cache statt HTTP-Calls.
Fallback auf HTTP nur für cross-group Relationen (über `orElseGet`).

**Vorgehen:** Bitte erste Option umsetzen

---

### P2-3 · `allTasks`-Cache in `HierarchiesController` veraltet

**Datei:** `HierarchiesController.java`, `loadGroup()` / `pickTask()`

`allTasks` wird beim Wechsel der Gruppe geladen und nie wieder aktualisiert. Im
"Vorgänger hinzufügen"-Dialog fehlen Tasks, die seit dem letzten Laden erstellt wurden.

**Lösung:** `allTasks` in `pickTask()` immer frisch laden (oder bei jeder CRUD-Operation
invalidieren).

**Vorgehen:** Bitte erste Option umsetzen

---

### P2-4 · Kein Fehler-Feedback im Graph-View

**Datei:** `frontend/fx/src/main/java/de/ruu/app/pragma/fx/task/graph/GraphController.java`, `loadGroup()`

Schlägt der Server-Aufruf fehl, fängt `loadGroup()` die Exception, loggt sie und zeigt
dem Benutzer nichts. Der Graph bleibt leer ohne erklärenden Hinweis.

**Lösung:** Im `catch`-Block `lblStatus` mit Fehlermeldung befüllen und/oder
`Alert`-Dialog anzeigen (analog zu `HierarchiesController.showError()`).

~~**Vorgehen:** Bitte zweite Option umsetzen~~ ✅ umgesetzt 2026-06-27

`catch`-Block zeigt jetzt `lblStatus` (persistent) und ruft `showError()` (Alert-Dialog).

---

### ~~P2-5 · Kein Scroll/Zoom im Graph~~ ✅ umgesetzt 2026-06-27

Graph-FXML nutzt jetzt `ScrollPane` (`pannable=true`). Canvas wird via `Group`-Wrapper
eingebettet (damit ScrollPane die visuellen Bounds berücksichtigt). Zoom via Mausrad
(`setOnScroll`) skaliert den Canvas zwischen 0.1× und 5×.

---

### P2-6 · Layout-Dateien sind an Datenbank-IDs gebunden

**Datei:** `GraphController.java`, `saveLayout()` / `loadLayout()`

Gespeicherte `.pgraph`-Dateien werden wertlos, sobald die Datenbank geleert und neu
befüllt wird (neue IDs). Das betrifft vor allem Entwicklungs-/Testumgebungen mit
`DBClear` + `DBPopulate`.

**Lösung:** Neben der ID auch den Task-Namen als Kommentar oder Fallback-Schlüssel
speichern; beim Laden zunächst per ID, dann per Name suchen.

**Vorgehen:** Bitte erstmal nicht umsetzen

---

### P2-7 · `hibernate.show_sql=true` und `traceSpecification=all` in Produktion

**Dateien:** `persistence.xml`, `server.xml`

Beide Einstellungen sind für die Entwicklung gedacht und erzeugen im Produktivbetrieb
massiven Log-Output und Performanceverlust.

**Lösung:** Logging-Konfiguration per Liberty-Environment-Variable (`server.env`)
übersteuern; `show_sql=false` und `traceSpecification=*=warning` in einem
Produktions-Profil setzen.

**Vorgehen:** Bitte erstmal nicht umsetzen

---

## P3 — Mittelfristig wichtig

### ~~P3-1 · Keine Unit-Tests für Entities und Mappings~~ ✅ umgesetzt 2026-06-27

47 JUnit-5-Tests in `bean/src/test/java/.../bean/`:
- `TaskBeanTest` (20) — bidirektionale Konsistenz von Predecessor/Successor/SubTask,
  Optional-Semantik (null = nicht geladen, empty Set = geladen aber leer), Scalar-Setter
- `TaskGroupBeanTest` (13) — addTask/removeTask, Task-Gruppen-Wechsel, Name-Setter
- `MappingsTest` (14) — Bean↔DTO-Roundtrips mit id/version, Predecessors/Successors,
  SubTask-Hierarchie, Context-Deduplizierung (gleiche DTO-Instanz → gleicher Bean)

`bean/pom.xml`: JUnit Jupiter + AssertJ als test-scope; maven-compiler-plugin
konfiguriert, module-info.java beim Test-Compile auszuschließen (unnamed module).

---

### ~~P3-2 · Keine Tests für Graph-Layout-Algorithmen~~ ✅ umgesetzt 2026-06-27

`GraphLayout.java` (package `de.ruu.app.pragma.fx.task.graph`) extrahiert:
`computeLayers()`, `resolveOverlaps()`, `avgPredY()` als `public static` Methoden.
`GraphController` delegiert intern dorthin. 16 JUnit-Tests in `GraphLayoutTest`:
Kette, Diamant, Ghost-Predecessor, disconnected Graph; resolveOverlaps (kein Überlapp,
alle gleich, Clamp auf PAD); avgPredY (kein Pred, ein Pred, zwei Preds, unplatzierter Pred).

---

### ~~P3-3 · Kein MicroProfile Health-Endpunkt konfiguriert~~ ✅ umgesetzt 2026-06-27

`PragmaHealthCheck` mit `@Liveness` + `@Readiness` + `@ApplicationScoped` angelegt;
prüft DB-Konnektivität via `SELECT 1`; erreichbar unter `/health`.

---

### ~~P3-4 · Kein HTTPS-Enforcement im REST-Client~~ ✅ umgesetzt 2026-06-27

`pragma.rest-api.scheme=http` in `microprofile-config.properties` ergänzt.
`TaskClient` und `TaskGroupClient` bauen URL jetzt aus `scheme + "://" + host + ":" + port`.

---

### ~~P3-5 · Kein Dirty-State-Tracking in FX-Views~~ ✅ umgesetzt 2026-06-27

`GanttController` und `HierarchiesController` haben jetzt:
- `dirty` / `updating` / `handlingNav` Flags
- Listener auf alle editierbaren Felder (DatePicker, TextArea, CheckBox) setzen `dirty = true`
  (außer wenn `updating = true` — programmatisches Füllen)
- Selektion (Aufgabe wechseln, Gruppe wechseln): `confirmDiscardChanges()` prüft `dirty` und
  zeigt Alert; bei Abbruch wird die alte Selektion wiederhergestellt (`handlingNav` guard)
- Nach erfolgreichem Speichern: `dirty = false`
- `fillDetail()` / `clearDetail()` wrappen Field-Zuweisungen in `updating = true/false`

---

## P4 — Fehlende Domänenfunktionen (nach Stabilisierung)

| #    | Feature                                                                  | Begründung                                                            |
|------|--------------------------------------------------------------------------|-----------------------------------------------------------------------|
| P4-1 | Status-Enum (TODO / IN_PROGRESS / BLOCKED / DONE) statt `closed`-Boolean | `closed=true` unterscheidet nicht zwischen "fertig" und "abgebrochen" |
| P4-2 | `actualStart` / `actualEnd`                                              | Nur Planung ohne Ist-Daten; Gantt zeigt keine Realität                |
| P4-3 | Assignee / Verantwortlicher                                              | Keine Personenzuordnung möglich                                       |
| P4-4 | Aufwandsschätzung (Stunden oder Story Points)                            | Kein Basismaterial für Kapazitätsplanung                              |
| P4-5 | Predecessor-Typ (FS / SS / FF / SF)                                      | Alle Abhängigkeiten sind implizit Finish-to-Start                     |
| P4-6 | Audit-Log (wer änderte was wann)                                         | Spurlosigkeit bei Datenproblemen                                      |
| P4-7 | Gantt-Export (PDF / Excel)                                               | Für Reporting und externe Kommunikation                               |
| P4-8 | Containerisierung (Dockerfile / Compose-Ausbau)                          | Reproduzierbare Deployments                                           |

---

## Empfohlene Reihenfolge

```
P1-3  (deleteAll-Bug + Schutz)          ← einfach, hohes Risiko
P1-4  (Input-Validierung)               ← einfach, stabilisiert API sofort
P1-5  (ExceptionMapper)                 ← einfach, verbessert alle Fehlerfälle
P1-2  (Optimistic Locking)              ← mittel, benötigt Client-Anpassung
P1-1  (hbm2ddl → Flyway)               ← aufwändig, aber Voraussetzung für alles weitere
P2-4  (Fehler-Feedback Graph)           ← trivial
P2-3  (allTasks-Cache)                  ← trivial
P2-7  (Logging in Produktion)           ← trivial
P2-2  (Rekursive Calls Hierarchies)     ← mittel
P2-1  (Paging)                          ← mittel
P2-5  (Scroll/Zoom Graph)              ← mittel
P3-1  (Unit-Tests Entities/Mappings)    ← parallel möglich
P3-2  (Tests Layout-Algorithmen)        ← parallel möglich
P3-4  (HTTPS-Scheme konfigurierbar)     ← klein
P3-3  (Health-Endpunkt)                 ← klein
P3-5  (Dirty-State)                     ← mittel
P2-6  (Layout-Datei robuster)           ← klein
P4-*  (Domänenfunktionen)               ← nach obigem Sprint
```
