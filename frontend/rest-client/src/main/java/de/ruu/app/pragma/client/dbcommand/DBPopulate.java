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
        populateDatabase(groupClient, taskClient);
        System.out.println("done");
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

        taskClient.setParentTask(f11, featureSet1);
        taskClient.setParentTask(f12, featureSet1);
        taskClient.setParentTask(f13, featureSet1);
        taskClient.setParentTask(f14, featureSet1);
        taskClient.setParentTask(f21, featureSet2);
        taskClient.setParentTask(f22, featureSet2);
        taskClient.setParentTask(f23, featureSet2);
        taskClient.setParentTask(f24, featureSet2);
        taskClient.setParentTask(f31, featureSet3);
        taskClient.setParentTask(f32, featureSet3);
        taskClient.setParentTask(f33, featureSet3);
        taskClient.setParentTask(f34, featureSet3);

        taskClient.addPredecessor(f12, f11);
        taskClient.addPredecessor(f13, f12);
        taskClient.addPredecessor(f14, f13);
        taskClient.addPredecessor(f22, f21);
        taskClient.addPredecessor(f23, f22);
        taskClient.addPredecessor(f24, f23);
        taskClient.addPredecessor(f32, f31);
        taskClient.addPredecessor(f33, f32);
        taskClient.addPredecessor(f34, f33);
        taskClient.addPredecessor(featureSet2, featureSet1);
        taskClient.addPredecessor(featureSet3, featureSet2);
        taskClient.addPredecessor(f21, f11); // predecessor outside parent/child hierarchy
    }
}
