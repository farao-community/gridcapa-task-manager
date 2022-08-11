/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskUpdateNotifier {
    private static final String TASK_UPDATED_BINDING = "task-updated";
    private static final String TASK_STATUS_UPDATED_BINDING = "task-status-updated";

    private final StreamBridge streamBridge;
    private final TaskDtoBuilder taskDtoBuilder;

    @Value("${stomp.notify}")
    private String notify;

    private final SimpMessagingTemplate broker;

    public TaskUpdateNotifier(StreamBridge streamBridge, TaskDtoBuilder taskDtoBuilder, SimpMessagingTemplate broker) {
        this.streamBridge = streamBridge;
        this.taskDtoBuilder = taskDtoBuilder;
        this.broker = broker;
    }

    public void notify(Task task, boolean withStatusUpdate) {
        String bindingName = withStatusUpdate ? TASK_STATUS_UPDATED_BINDING : TASK_UPDATED_BINDING;
        TaskDto taskdto = taskDtoBuilder.createDtoFromEntity(task);
        streamBridge.send(bindingName, taskdto);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        broker.convertAndSend(notify + "/update/" + fmt.format(task.getTimestamp()), taskdto);
        broker.convertAndSend(notify + "/update/" + fmt.format(task.getTimestamp()).substring(0, 10), taskdto);

    }

    public void notify(Set<TaskWithStatusUpdate> taskWithStatusUpdateSet) {
        taskWithStatusUpdateSet.parallelStream().forEach(t -> notify(t.getTask(), t.isStatusUpdated()));
    }
}
