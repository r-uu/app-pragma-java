# Backlog — Schwachstellen und offene Punkte

Zuletzt aktualisiert: 2026-06-28. Bezieht sich auf den aktuellen Stand der `main`-Branch.

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

**Vorgehen:** Bewusst zurückgestellt, solange das Daten-/Objektmodell noch nicht final ist
und DB-Verluste tolerierbar sind. Wird relevant, sobald DB-Backup/Restore in die UI
integriert werden soll (→ P4-8 Containerisierung).

---

## P2 — Starke Einschränkungen im Alltag

### P2-6 · Layout-Dateien sind an Datenbank-IDs gebunden

**Datei:** `frontend/fx/src/main/java/de/ruu/app/pragma/fx/task/graph/GraphController.java`,
`saveLayout()` / `loadLayout()`

Gespeicherte `.pgraph`-Dateien werden wertlos, sobald die Datenbank geleert und neu
befüllt wird (neue IDs). Das betrifft vor allem Entwicklungs-/Testumgebungen mit
`DBClear` + `DBPopulate`.

**Lösung:** Neben der ID auch den Task-Namen als Kommentar oder Fallback-Schlüssel
speichern; beim Laden zunächst per ID, dann per Name suchen.

**Vorgehen:** Zurückgestellt — erstmal nicht umsetzen.

---

### P2-7 · `hibernate.show_sql=true` und `traceSpecification=all` in Produktion

**Dateien:** `backend/rest/src/main/resources/META-INF/persistence.xml`,
`backend/rest/src/main/liberty/config/server.xml`

Beide Einstellungen sind für die Entwicklung gedacht und erzeugen im Produktivbetrieb
massiven Log-Output und Performanceverlust.

**Lösung:** Logging-Konfiguration per Liberty-Environment-Variable (`server.env`)
übersteuern; `show_sql=false` und `traceSpecification=*=warning` in einem
Produktions-Profil setzen.

**Vorgehen:** Zurückgestellt — erstmal nicht umsetzen.

---

## P3 — Mittelfristig wichtig

### P3-6 · SmartGraph-Dependency ungenutzt

**Datei:** `frontend/fx/pom.xml`

SmartGraph 2.0.0 ist als Dependency deklariert, wird aber nicht für das visuelle Rendering
verwendet (unterstützt keine abgerundeten Rechtecke als Knoten). `smartgraph.css` liegt
noch im Classpath.

**Optionen:**
- Dependency entfernen und CSS löschen (sauberste Lösung).
- Dependency als optional markieren und Kommentar hinterlassen, falls eine spätere
  SmartGraph-Version Rechteck-Knoten unterstützt.

---

### P3-7 · Keine Tests für `TaskFx` / `TaskGroupFx`

**Dateien:** `frontend/fx/src/main/java/de/ruu/app/pragma/fx/TaskFx.java`,
`frontend/fx/src/main/java/de/ruu/app/pragma/fx/TaskGroupFx.java`

Die JavaFX-Observable-Wrapper haben kein Testmodul. Bidirektionale Property-Bindungen
und die Delegierung an das Bean-Modell sind ungetestet.

**Lösung:** JUnit-Tests ohne Headless-FX-Initialisierung durch TestFX oder durch
Verzicht auf FX-spezifische Assertions, die keine Stage benötigen (Property-Werte lassen
sich ohne FX-Toolkit prüfen, sobald `Platform.startup()` einmalig aufgerufen wird).

---

### P3-8 · Keine Unit-Tests für REST-Ressourcen

**Dateien:** `backend/rest/src/main/java/de/ruu/app/pragma/rest/TaskResource.java`,
`backend/rest/src/main/java/de/ruu/app/pragma/rest/TaskGroupResource.java`

Die REST-Schicht wird ausschließlich durch Integrationstests in `frontend/rest-client/src/test`
abgedeckt (benötigen laufenden Liberty-Server). Fehler in Mapping-Logik oder Version-Check
werden nur durch Integrationstests gefangen.

**Lösung:** Jersey Test Framework oder MicroShed Testing für Unit-Tests der REST-Endpunkte
ohne vollständigen Liberty-Server.

---

## P4 — Fehlende Domänenfunktionen (nach Stabilisierung)

| #    | Feature                                                                   | Begründung                                                                             |
|------|---------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| P4-1 | Status-Enum (TODO / IN_PROGRESS / BLOCKED / DONE) statt `closed`-Boolean  | `closed=true` unterscheidet nicht zwischen "fertig" und "abgebrochen"                  |
| P4-2 | `actualStart` / `actualEnd`                                               | Nur Planung ohne Ist-Daten; Gantt zeigt keine Realität                                 |
| P4-3 | Assignee / Verantwortlicher                                               | Keine Personenzuordnung möglich                                                        |
| P4-4 | Aufwandsschätzung (Stunden oder Story Points)                             | Kein Basismaterial für Kapazitätsplanung                                               |
| P4-5 | Predecessor-Typ (FS / SS / FF / SF)                                       | Alle Abhängigkeiten sind implizit Finish-to-Start                                      |
| P4-6 | Audit-Log (wer änderte was wann)                                          | Spurlosigkeit bei Datenproblemen                                                       |
| P4-7 | Gantt-Export (PDF / Excel)                                                | Für Reporting und externe Kommunikation                                                |
| P4-8 | Containerisierung (Dockerfile / Compose-Ausbau)                           | Reproduzierbare Deployments; Voraussetzung für sinnvollen P1-1-Einsatz                 |
| P4-9 | mapstruct-Evaluierung                                                     | context.md sieht Evaluierung vor; aktuell manuelle Mappings-Klassen in allen Schichten |

---

## Empfohlene Reihenfolge

```
P3-6  (SmartGraph-Cleanup)            ← trivial, saubert Classpath
P3-7  (Tests TaskFx/TaskGroupFx)      ← parallel möglich
P3-8  (REST Unit-Tests)               ← parallel möglich
P2-7  (Logging Produktion)            ← trivial, wenn Produktiveinsatz näher rückt
P2-6  (Layout-Datei robuster)         ← klein, wenn DBClear häufig genutzt wird
P4-8  (Containerisierung)             ← Voraussetzung für P1-1
P1-1  (hbm2ddl → Flyway)             ← aufwändig, nach P4-8
P4-1  (Status-Enum)                   ← klare Domänenverbesserung
P4-2  (actualStart/actualEnd)         ← sinnvoll nach P4-1
P4-*  (restliche Domänenfunktionen)   ← nach obigem Sprint
P4-9  (mapstruct)                     ← strategisch, nach Stabilisierung evaluieren
```
