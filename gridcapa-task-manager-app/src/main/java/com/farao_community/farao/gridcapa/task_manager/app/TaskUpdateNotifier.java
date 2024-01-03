/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.WebsocketConfig;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
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

    private final SimpMessagingTemplate stompBridge;
    private final WebsocketConfig websocketConfig;

    public TaskUpdateNotifier(StreamBridge streamBridge, TaskDtoBuilder taskDtoBuilder, SimpMessagingTemplate broker, WebsocketConfig websocketConfig) {
        this.streamBridge = streamBridge;
        this.taskDtoBuilder = taskDtoBuilder;
        this.stompBridge = broker;
        this.websocketConfig = websocketConfig;
    }

    public void notify(Task task, boolean withStatusUpdate) {
        String bindingName = withStatusUpdate ? TASK_STATUS_UPDATED_BINDING : TASK_UPDATED_BINDING;
        TaskDto taskdto = taskDtoBuilder.createDtoFromEntity(task);
        TaskDto taskdtoNoLogs = taskDtoBuilder.createDtoFromEntityNoLogs(task);
        streamBridge.send(bindingName, taskdto);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        stompBridge.convertAndSend(websocketConfig.getNotify() + "/update/" + fmt.format(task.getTimestamp()), taskdto); //to actualize the timestamp view
        stompBridge.convertAndSend(websocketConfig.getNotify() + "/update/" + fmt.format(task.getTimestamp()).substring(0, 10), taskdtoNoLogs); //to actualize the business date view
    }

    public void notify(Set<TaskWithStatusUpdate> taskWithStatusUpdateSet) {
        taskWithStatusUpdateSet.parallelStream().forEach(t -> notify(t.getTask(), t.isStatusUpdated()));
    }
}
