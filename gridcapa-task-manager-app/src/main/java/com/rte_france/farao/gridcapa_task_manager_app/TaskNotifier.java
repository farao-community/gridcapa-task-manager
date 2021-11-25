/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.farao.gridcapa_task_manager_app;

import com.rte_france.farao.gridcapa_task_manager_api.entities.Task;
import com.rte_france.farao.gridcapa_task_manager_api.entities.TaskDto;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskNotifier {
    private static final String TASK_UPDATED_BINDING = "task-updated";

    private final StreamBridge streamBridge;

    public TaskNotifier(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void notifyUpdate(Task task) {
        streamBridge.send(TASK_UPDATED_BINDING, TaskDto.fromEntity(task));
    }
}
