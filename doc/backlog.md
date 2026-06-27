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

### P1-2 · Optimistic Locking ist konfiguriert aber wirkungslos

**Datei:** `backend/rest/src/main/java/de/ruu/app/pragma/rest/TaskResource.java`, `update()`

`TaskJPA` besitzt ein `@Version`-Feld, das bei gleichzeitigen Schreibzugriffen schützen
soll. Der REST-`update()`-Endpunkt ignoriert die Version aus dem DTO jedoch vollständig —
der zweite Speichern-Klick überschreibt den ersten kommentarlos.

**Lösung:** Version aus `TaskDto` in `requireTask()` vor dem Merge prüfen; bei Konflikt
`409 Conflict` zurückgeben. Client muss die Version mitsenden und bei 409 neu laden.

**Vorgehen: ** Bitte umsetzen und prüfen, ob das Problem bei TaskGroup auch besteht. Falls
ja, bitte ebenfalls umsetzen.

---

### P1-3 · `deleteAll()` ungeschützt und mit falschem Tabellennamen

**Datei:** `backend/rest/src/main/java/de/ruu/app/pragma/rest/TaskGroupResource.java`

Zwei Probleme in einem:

1. `DELETE FROM task_predecessor` — die JoinTable heißt `task_predecessors_successors`;
   dieser native Query würde scheitern oder die falsche Tabelle treffen.
2. Der Endpunkt (`DELETE /task-groups`) löscht die gesamte Datenbank ohne jede
   Autorisierungsprüfung, obwohl Basic Auth in `10-basic-registry.xml` konfiguriert ist.

**Lösung:** Nativen Query auf korrekten Tabellennamen korrigieren; Endpunkt mit
`@RolesAllowed("admin")` absichern oder ganz entfernen (nur noch über `DBClear` nutzbar).

**Vorgehen:** Bitte vollständig entfernen und ggf. durch DBClear ersetzen.

---

### P1-4 · Keine Input-Validierung auf REST-Ebene

**Datei:** `TaskResource.java` (`TaskCreateRequest`), `TaskGroupResource.java`

DTOs und Request-Records haben keine Bean-Validation-Annotationen (`@NotNull`, `@Size`,
`@NotBlank`). Ein leerer Task-Name oder eine negative ID landen ungeprüft in der
Datenbank. Es gibt keinen `ExceptionMapper` für `ConstraintViolationException`.

**Lösung:** `@Valid` auf alle Request-Parameter; Annotationen auf Records/DTOs;
`ExceptionMapper<ConstraintViolationException>` → `400 Bad Request` mit sprechendem Body.

---

### P1-5 · Kein `ExceptionMapper` — interne Fehler als 500 durchgereicht

**Datei:** `backend/rest/src/main/java/de/ruu/app/pragma/rest/`

Unbehandelte `RuntimeException` führt zu einem leeren oder HTML-haltigen 500-Response.
Der REST-Client wirft daraufhin eine generische `RuntimeException("HTTP 500 …")`, die im
FX-Controller als leeres Fenster oder stilles Log-Statement endet.

**Lösung:** `ExceptionMapper<Exception>` → RFC 7807 Problem-Detail-JSON;
spezifische Mapper für `NotFoundException`, `ConstraintViolationException` etc.

---

## P2 — Starke Einschränkungen im Alltag

### P2-1 · Kein Paging bei `findAll()`

**Dateien:** `TaskResource.java`, `TaskGroupResource.java`, beide Clients

Alle Tasks bzw. Gruppen werden ungepaged geladen. Bei einigen Hundert Tasks bricht
Latenz und Speicherverbrauch spürbar ein.

**Lösung:** `@QueryParam("page")` / `@QueryParam("size")` einführen;
`TypedQuery.setFirstResult()` / `setMaxResults()`; Clients entsprechend erweitern.

---

### P2-2 · Rekursive REST-Calls in `HierarchiesController`

**Datei:** `frontend/fx/src/main/java/de/ruu/app/pragma/fx/task/hierarchy/HierarchiesController.java`

`buildPredecessorNode()` und `buildSuccessorNode()` machen je einen HTTP-Request pro
Node, rekursiv auf dem FX-Thread. Eine Vorgängerkette von 10 Tasks erzeugt 10+ synchrone
Requests — die UI friert ein.

**Lösung:** Vorhandenen `findByIdWithRelated`-Endpunkt verwenden und die transitive
Auflösung client-seitig aus dem bereits geladenen Graphen durchführen; alternativ einen
dedizierten Endpunkt `GET /tasks/{id}/predecessors?transitive=true` anbieten.

---

### P2-3 · `allTasks`-Cache in `HierarchiesController` veraltet

**Datei:** `HierarchiesController.java`, `loadGroup()` / `pickTask()`

`allTasks` wird beim Wechsel der Gruppe geladen und nie wieder aktualisiert. Im
"Vorgänger hinzufügen"-Dialog fehlen Tasks, die seit dem letzten Laden erstellt wurden.

**Lösung:** `allTasks` in `pickTask()` immer frisch laden (oder bei jeder CRUD-Operation
invalidieren).

---

### P2-4 · Kein Fehler-Feedback im Graph-View

**Datei:** `frontend/fx/src/main/java/de/ruu/app/pragma/fx/task/graph/GraphController.java`, `loadGroup()`

Schlägt der Server-Aufruf fehl, fängt `loadGroup()` die Exception, loggt sie und zeigt
dem Benutzer nichts. Der Graph bleibt leer ohne erklärenden Hinweis.

**Lösung:** Im `catch`-Block `lblStatus` mit Fehlermeldung befüllen und/oder
`Alert`-Dialog anzeigen (analog zu `HierarchiesController.showError()`).

---

### P2-5 · Kein Scroll/Zoom im Graph

**Datei:** `GraphController.java`, `buildGraph()`

Der Canvas ist ein einfaches `Pane`. Mit mehr als ~15 Nodes verschwinden Knoten außerhalb
des sichtbaren Bereichs; es gibt weder Scroll- noch Zoom-Funktion.

**Lösung:** Canvas in `ScrollPane` einbetten; Zoom via `Scale`-Transform auf dem Canvas,
gesteuert durch Mausrad (`ScrollEvent`).

---

### P2-6 · Layout-Dateien sind an Datenbank-IDs gebunden

**Datei:** `GraphController.java`, `saveLayout()` / `loadLayout()`

Gespeicherte `.pgraph`-Dateien werden wertlos, sobald die Datenbank geleert und neu
befüllt wird (neue IDs). Das betrifft vor allem Entwicklungs-/Testumgebungen mit
`DBClear` + `DBPopulate`.

**Lösung:** Neben der ID auch den Task-Namen als Kommentar oder Fallback-Schlüssel
speichern; beim Laden zunächst per ID, dann per Name suchen.

---

### P2-7 · `hibernate.show_sql=true` und `traceSpecification=all` in Produktion

**Dateien:** `persistence.xml`, `server.xml`

Beide Einstellungen sind für die Entwicklung gedacht und erzeugen im Produktivbetrieb
massiven Log-Output und Performanceverlust.

**Lösung:** Logging-Konfiguration per Liberty-Environment-Variable (`server.env`)
übersteuern; `show_sql=false` und `traceSpecification=*=warning` in einem
Produktions-Profil setzen.

---

## P3 — Mittelfristig wichtig

### P3-1 · Keine Unit-Tests für Entities und Mappings

**Dateien:** `TaskJPA.java`, `TaskBean.java`, `bean/Mappings.java`, `rest/Mappings.java`

Die Geschäftslogik in `addPredecessor`, `removePredecessor` (bidirektionale Konsistenz),
der zyklus-sichere Mapping-Kontext sowie die `Optional.empty() = nicht geladen`-Semantik
sind aktuell rein manuell verifizierbar.

**Lösung:** JUnit-5-Unit-Tests für Entities (ohne DB); Parametrisierte Tests für
Mapping-Roundtrips Bean ↔ DTO ↔ JPA.

---

### P3-2 · Keine Tests für Graph-Layout-Algorithmen

**Datei:** `GraphController.java`: `computeLayers()`, `resolveOverlaps()`, `avgPredY()`

Diese Methoden sind algorithmisch komplex (Kahn, Überlappungsauflösung, Ghost-Node-
Behandlung) und ändern sich erfahrungsgemäß häufig. Regressionen sind ohne Tests unsichtbar.

**Lösung:** Algorithmen in eine testbare, UI-unabhängige Klasse `GraphLayout` extrahieren;
JUnit-Tests mit kontrollierten Graphen (Kette, Baum, Zyklusversuch, Ghosts).

---

### P3-3 · Kein MicroProfile Health-Endpunkt konfiguriert

**Datei:** `server.xml` (Feature `microProfile-6.1` ist geladen, aber `/health` nicht genutzt)

Ohne Health-Endpunkt kann kein Load-Balancer oder Orchestrator (k8s, Nomad) den Status
der Instanz prüfen.

**Lösung:** `@Liveness` / `@Readiness`-Bean anlegen; DB-Konnektivität als Readiness-Check.

---

### P3-4 · Kein HTTPS-Enforcement im REST-Client

**Datei:** `TaskClient.java`, `TaskGroupClient.java`

Der Client baut die URL aus `http://` + Host + Port zusammen. Die TLS-Konfiguration im
`server.xml` wird damit nie genutzt; Credentials wandern im Klartext.

**Lösung:** Protokoll (`http`/`https`) als konfigurierbare Property
(`pragma.rest-api.scheme`) in `microprofile-config.properties` auslagern.

---

### P3-5 · Kein Dirty-State-Tracking in FX-Views

**Dateien:** `GanttController.java`, `HierarchiesController.java`

Wenn der Benutzer Felder editiert aber nicht speichert und dann die Gruppe wechselt
oder die Auswahl ändert, gehen Änderungen kommentarlos verloren.

**Lösung:** Properties der Eingabefelder binden und bei Änderung einen "ungespeichert"-
Flag setzen; beim Navigieren wegweisen mit Bestätigungsdialog.

---

## P4 — Fehlende Domänenfunktionen (nach Stabilisierung)

| # | Feature | Begründung |
|---|---|---|
| P4-1 | Status-Enum (TODO / IN_PROGRESS / BLOCKED / DONE) statt `closed`-Boolean | `closed=true` unterscheidet nicht zwischen "fertig" und "abgebrochen" |
| P4-2 | `actualStart` / `actualEnd` | Nur Planung ohne Ist-Daten; Gantt zeigt keine Realität |
| P4-3 | Assignee / Verantwortlicher | Keine Personenzuordnung möglich |
| P4-4 | Aufwandsschätzung (Stunden oder Story Points) | Kein Basismaterial für Kapazitätsplanung |
| P4-5 | Predecessor-Typ (FS / SS / FF / SF) | Alle Abhängigkeiten sind implizit Finish-to-Start |
| P4-6 | Audit-Log (wer änderte was wann) | Spurlosigkeit bei Datenproblemen |
| P4-7 | Gantt-Export (PDF / Excel) | Für Reporting und externe Kommunikation |
| P4-8 | Containerisierung (Dockerfile / Compose-Ausbau) | Reproduzierbare Deployments |

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
