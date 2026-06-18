package de.ruu.app.pragma.bean;

import de.ruu.app.pragma.core.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskBean implements Task<TaskBean>
{
    private Long           id;
    private String         title;
    private TaskBean       parentTask;
    private List<TaskBean> subTasks = new ArrayList<>();

    public TaskBean(String title) { this.title = title; }

    @Override public Long           getId()                        { return id;         }
    @Override public String         getTitle()                     { return title;      }
    @Override public void           setTitle(String title)         { this.title = title; }
    @Override public TaskBean       getParentTask()                { return parentTask; }
    @Override public void           setParentTask(TaskBean parent) { this.parentTask = parent; }
    @Override public List<TaskBean> getSubTasks()                  { return subTasks;   }
}
