/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskUpdateNotifier {
    private static final String TASK_UPDATED_BINDING = "task-updated";

    private final StreamBridge streamBridge;
    private final TaskDtoBuilder taskDtoBuilder;

    public TaskUpdateNotifier(StreamBridge streamBridge, TaskDtoBuilder taskDtoBuilder) {
        this.streamBridge = streamBridge;
        this.taskDtoBuilder = taskDtoBuilder;
    }

    public void notify(Task task) {
        streamBridge.send(TASK_UPDATED_BINDING, taskDtoBuilder.createDtoFromEntity(task));
    }

    public void notify(Set<Task> tasks) {
        tasks.parallelStream().forEach(task -> streamBridge.send(TASK_UPDATED_BINDING, taskDtoBuilder.createDtoFromEntity(task)));
    }
}
