/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

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
