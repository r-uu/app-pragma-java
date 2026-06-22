package de.ruu.app.pragma.client.dbcommand;

import de.ruu.app.pragma.client.TaskGroupClient;

public class DBClear
{
    public static void main(String[] args)
    {
        TaskGroupClient groupClient = new TaskGroupClient();
        groupClient.postConstruct();
        try
        {
            run(groupClient);
        }
        finally
        {
            groupClient.preDestroy();
        }
    }

    public static void run(TaskGroupClient groupClient)
    {
        System.out.println("clearing database ...");
        groupClient.deleteAll();
        System.out.println("done");
    }
}
