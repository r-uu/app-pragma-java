package de.ruu.app.pragma.client.dbcommand;

import de.ruu.app.pragma.bean.TaskBean;
import de.ruu.app.pragma.bean.TaskGroupBean;
import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;

import java.time.LocalDate;

public class DBPopulate
{
    public static void main(String[] args)
    {
        TaskGroupClient groupClient = new TaskGroupClient();
        groupClient.postConstruct();
        TaskClient taskClient = new TaskClient();
        taskClient.postConstruct();
        try
        {
            run(groupClient, taskClient);
        }
        finally
        {
            taskClient.preDestroy();
            groupClient.preDestroy();
        }
    }

    public static void run(TaskGroupClient groupClient, TaskClient taskClient)
    {
        System.out.println("populating database ...");

        // ── Gruppe 1: Analyse ────────────────────────────────────────────────
        TaskGroupBean analyse = groupClient.create(new TaskGroupBean("Analyse"));
        TaskBean a1 = taskClient.create(new TaskBean(analyse, "Anforderungen erfassen"));
        TaskBean a2 = taskClient.create(new TaskBean(analyse, "Technologie-Stack festlegen"));
        TaskBean a3 = taskClient.create(new TaskBean(analyse, "Architektur definieren"));

        taskClient.addPredecessor(a2.id(), a1.id()); // A2 nach A1
        taskClient.addPredecessor(a3.id(), a2.id()); // A3 nach A2

        // ── Gruppe 2: Entwicklung ────────────────────────────────────────────
        TaskGroupBean entwicklung = groupClient.create(new TaskGroupBean("Entwicklung"));

        TaskBean d1  = taskClient.create(new TaskBean(entwicklung, "Backend implementieren"));
        TaskBean d1a = taskClient.create(new TaskBean(entwicklung, "REST-Endpoints"));
        TaskBean d1b = taskClient.create(new TaskBean(entwicklung, "JPA-Entities"));
        TaskBean d2  = taskClient.create(new TaskBean(entwicklung, "Frontend implementieren"));

        taskClient.addPredecessor(d1.id() , a3.id());   // D1 nach A3
        taskClient.setParentTask (d1a.id(), d1.id());   // d1a ist Teilaufgabe von d1
        taskClient.setParentTask (d1b.id(), d1.id());   // d1b ist Teilaufgabe von d1
        taskClient.addPredecessor(d2.id() , d1.id());   // D2 nach D1

        // ── Gruppe 3: Test ───────────────────────────────────────────────────
        TaskGroupBean test = groupClient.create(new TaskGroupBean("Test"));
        TaskBean t1 = taskClient.create(new TaskBean(test, "Integrationstests"));
        TaskBean t2 = taskClient.create(new TaskBean(test, "Abnahmetests"));

        taskClient.addPredecessor(t1.id(), d1.id());  // T1 nach D1
        taskClient.addPredecessor(t2.id(), t1.id());  // T2 nach T1
        taskClient.addPredecessor(t2.id(), d2.id());  // T2 auch nach D2

        System.out.println("done — 3 groups, 10 tasks");

        populateDatabase(groupClient, taskClient);
    }

    private static void populateDatabase(TaskGroupClient groupClient, TaskClient taskClient)
    {
        TaskGroupBean project = groupClient.create(new TaskGroupBean("project jeeeraaah"));

        TaskBean featureSet1 = taskClient.create(new TaskBean(project, "feature set 1")
                .plannedStart(LocalDate.of(2025, 1,  1))
                .plannedEnd  (LocalDate.of(2025, 1, 31)));
        TaskBean featureSet2 = taskClient.create(new TaskBean(project, "feature set 2")
                .plannedStart(LocalDate.of(2025, 1,  8))
                .plannedEnd  (LocalDate.of(2025, 2,  7)));
        TaskBean featureSet3 = taskClient.create(new TaskBean(project, "feature set 3")
                .plannedStart(LocalDate.of(2025, 1, 17))
                .plannedEnd  (LocalDate.of(2025, 2, 15)));

        TaskBean f11 = taskClient.create(new TaskBean(project, "feature 1.1 - analyse"  ).plannedStart(LocalDate.of(2025, 1,  1)).plannedEnd(LocalDate.of(2025, 1,  7)));
        TaskBean f12 = taskClient.create(new TaskBean(project, "feature 1.2 - design"   ).plannedStart(LocalDate.of(2025, 1,  8)).plannedEnd(LocalDate.of(2025, 1, 15)));
        TaskBean f13 = taskClient.create(new TaskBean(project, "feature 1.3 - implement").plannedStart(LocalDate.of(2025, 1, 16)).plannedEnd(LocalDate.of(2025, 1, 25)));
        TaskBean f14 = taskClient.create(new TaskBean(project, "feature 1.4 - test"     ).plannedStart(LocalDate.of(2025, 1, 26)).plannedEnd(LocalDate.of(2025, 1, 31)));
        TaskBean f21 = taskClient.create(new TaskBean(project, "feature 2.1 - analyse"  ).plannedStart(LocalDate.of(2025, 1,  8)).plannedEnd(LocalDate.of(2025, 1, 15)));
        TaskBean f22 = taskClient.create(new TaskBean(project, "feature 2.2 - design"   ).plannedStart(LocalDate.of(2025, 1, 16)).plannedEnd(LocalDate.of(2025, 1, 25)));
        TaskBean f23 = taskClient.create(new TaskBean(project, "feature 2.3 - implement").plannedStart(LocalDate.of(2025, 1, 26)).plannedEnd(LocalDate.of(2025, 1, 31)));
        TaskBean f24 = taskClient.create(new TaskBean(project, "feature 2.4 - test"     ).plannedStart(LocalDate.of(2025, 2,  1)).plannedEnd(LocalDate.of(2025, 2,  7)));
        TaskBean f31 = taskClient.create(new TaskBean(project, "feature 3.1 - analyse"  ).plannedStart(LocalDate.of(2025, 1, 17)).plannedEnd(LocalDate.of(2025, 1, 25)));
        TaskBean f32 = taskClient.create(new TaskBean(project, "feature 3.2 - design"   ).plannedStart(LocalDate.of(2025, 1, 26)).plannedEnd(LocalDate.of(2025, 1, 31)));
        TaskBean f33 = taskClient.create(new TaskBean(project, "feature 3.3 - implement").plannedStart(LocalDate.of(2025, 2,  1)).plannedEnd(LocalDate.of(2025, 2,  7)));
        TaskBean f34 = taskClient.create(new TaskBean(project, "feature 3.4 - test"     ).plannedStart(LocalDate.of(2025, 2,  8)).plannedEnd(LocalDate.of(2025, 2, 15)));

        taskClient.setParentTask(f11.id(), featureSet1.id());
        taskClient.setParentTask(f12.id(), featureSet1.id());
        taskClient.setParentTask(f13.id(), featureSet1.id());
        taskClient.setParentTask(f14.id(), featureSet1.id());
        taskClient.setParentTask(f21.id(), featureSet2.id());
        taskClient.setParentTask(f22.id(), featureSet2.id());
        taskClient.setParentTask(f23.id(), featureSet2.id());
        taskClient.setParentTask(f24.id(), featureSet2.id());
        taskClient.setParentTask(f31.id(), featureSet3.id());
        taskClient.setParentTask(f32.id(), featureSet3.id());
        taskClient.setParentTask(f33.id(), featureSet3.id());
        taskClient.setParentTask(f34.id(), featureSet3.id());

        taskClient.addPredecessor(f12.id(), f11.id());
        taskClient.addPredecessor(f13.id(), f12.id());
        taskClient.addPredecessor(f14.id(), f13.id());
        taskClient.addPredecessor(f22.id(), f21.id());
        taskClient.addPredecessor(f23.id(), f22.id());
        taskClient.addPredecessor(f24.id(), f23.id());
        taskClient.addPredecessor(f32.id(), f31.id());
        taskClient.addPredecessor(f33.id(), f32.id());
        taskClient.addPredecessor(f34.id(), f33.id());
        taskClient.addPredecessor(featureSet2.id(), featureSet1.id());
        taskClient.addPredecessor(featureSet3.id(), featureSet2.id());
    }
}
