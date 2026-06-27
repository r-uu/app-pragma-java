package de.ruu.app.pragma.client.dbcommand;

import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;

public class DBClear
{
    public static void main(String[] args)
    {
        TaskGroupClient groupClient = new TaskGroupClient();
        TaskClient      taskClient  = new TaskClient();
        groupClient.postConstruct();
        taskClient .postConstruct();
        try
        {
            run(groupClient, taskClient);
        }
        finally
        {
            taskClient .preDestroy();
            groupClient.preDestroy();
        }
    }

    public static void run(TaskGroupClient groupClient, TaskClient taskClient)
    {
        System.out.println("clearing database ...");
        // Remove all predecessor/successor relationships first to avoid FK constraint violations
        // when deleting tasks (task_predecessors_successors references tasks by FK).
        taskClient.findAll().forEach(task ->
            taskClient.findPredecessors(task).forEach(pred ->
                taskClient.removePredecessor(task, pred)));
        // Delete all groups — cascades to their tasks (and sub-task hierarchies).
        groupClient.findAll().forEach(groupClient::delete);
        System.out.println("done");
    }
}
