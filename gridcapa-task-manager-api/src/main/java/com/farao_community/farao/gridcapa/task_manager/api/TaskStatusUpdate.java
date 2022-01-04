/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class TaskStatusUpdate {
    private final UUID id;
    private final TaskStatus taskStatus;

    @JsonCreator
    public TaskStatusUpdate(@JsonProperty("id") UUID id,
                            @JsonProperty("taskStatus") TaskStatus taskStatus) {
        this.id = id;
        this.taskStatus = taskStatus;
    }

    public UUID getId() {
        return id;
    }

    public TaskStatus getTaskStatus() {
        return taskStatus;
    }
}
