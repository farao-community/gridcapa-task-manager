package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class TaskWithStatusUpdate {
    private final Task task;
    private boolean statusUpdated;

    public TaskWithStatusUpdate(Task task, boolean statusUpdated) {
        this.task = task;
        this.statusUpdated = statusUpdated;
    }

    public Task getTask() {
        return task;
    }

    public boolean isStatusUpdated() {
        return statusUpdated;
    }

    public void setStatusUpdated(boolean statusUpdated) {
        this.statusUpdated = statusUpdated;
    }
}
