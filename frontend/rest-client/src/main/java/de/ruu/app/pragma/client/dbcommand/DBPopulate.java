package de.ruu.app.pragma.client.dbcommand;

import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;
import de.ruu.app.pragma.dto.TaskDto;
import de.ruu.app.pragma.dto.TaskGroupDto;

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
        TaskGroupDto analyse = groupClient.create(new TaskGroupDto("Analyse"));
        long gid1 = analyse.id();
        TaskDto a1 = taskClient.create("Anforderungen erfassen",      gid1);
        TaskDto a2 = taskClient.create("Technologie-Stack festlegen", gid1);
        TaskDto a3 = taskClient.create("Architektur definieren",      gid1);

        taskClient.addPredecessor(a2.id(), a1.id()); // A2 nach A1
        taskClient.addPredecessor(a3.id(), a2.id()); // A3 nach A2

        // ── Gruppe 2: Entwicklung ────────────────────────────────────────────
        TaskGroupDto entwicklung = groupClient.create(new TaskGroupDto("Entwicklung"));
        long gid2 = entwicklung.id();
        TaskDto d1   = taskClient.create("Backend implementieren",   gid2);
        TaskDto d1a  = taskClient.create("REST-Endpoints",           gid2);
        TaskDto d1b  = taskClient.create("JPA-Entities",             gid2);
        TaskDto d2   = taskClient.create("Frontend implementieren",  gid2);

        taskClient.addPredecessor(d1.id(), a3.id());   // D1 nach A3
        taskClient.setParentTask(d1a.id(), d1.id());   // d1a ist Teilaufgabe von d1
        taskClient.setParentTask(d1b.id(), d1.id());   // d1b ist Teilaufgabe von d1
        taskClient.addPredecessor(d2.id(), d1.id());   // D2 nach D1

        // ── Gruppe 3: Test ───────────────────────────────────────────────────
        TaskGroupDto test = groupClient.create(new TaskGroupDto("Test"));
        long gid3 = test.id();
        TaskDto t1 = taskClient.create("Integrationstests",  gid3);
        TaskDto t2 = taskClient.create("Abnahmetests",        gid3);

        taskClient.addPredecessor(t1.id(), d1.id());  // T1 nach D1
        taskClient.addPredecessor(t2.id(), t1.id());  // T2 nach T1
        taskClient.addPredecessor(t2.id(), d2.id());  // T2 auch nach D2

        System.out.println("done — 3 groups, 10 tasks");
    }
}
