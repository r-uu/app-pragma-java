package de.ruu.app.pragma.client.dbcommand;

import de.ruu.app.pragma.client.TaskClient;
import de.ruu.app.pragma.client.TaskGroupClient;

public class DBCommand
{
    public static void main(String[] args)
    {
        TaskGroupClient groupClient = new TaskGroupClient();
        groupClient.postConstruct();
        TaskClient taskClient = new TaskClient();
        taskClient.postConstruct();
        try
        {
            DBClear.run(groupClient);
            DBPopulate.run(groupClient, taskClient);
        }
        finally
        {
            taskClient.preDestroy();
            groupClient.preDestroy();
        }
    }
}
