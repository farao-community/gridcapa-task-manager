/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.WebsocketConfig;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.entities.TaskWithStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.service.TaskDtoBuilderService;
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
    private static final String TASK_STATUS_UPDATED_BINDING = "task-status-updated";
    private static final String TASK_INPUT_UPDATED_BINDING = "task-input-updated";

    private final StreamBridge streamBridge;
    private final TaskDtoBuilderService taskDtoBuilderService;

    private final SimpMessagingTemplate stompBridge;
    private final WebsocketConfig websocketConfig;

    public TaskUpdateNotifier(StreamBridge streamBridge, TaskDtoBuilderService taskDtoBuilderService, SimpMessagingTemplate broker, WebsocketConfig websocketConfig) {
        this.streamBridge = streamBridge;
        this.taskDtoBuilderService = taskDtoBuilderService;
        this.stompBridge = broker;
        this.websocketConfig = websocketConfig;
    }

    public void notify(Task task, boolean withStatusUpdate, boolean withEventsUpdate) {
        this.notify(task, withStatusUpdate, withEventsUpdate, false);
    }

    public void notify(final Task task, final boolean withStatusUpdate, final boolean withEventsUpdate, final boolean withNewInput) {
        final TaskDto taskDtoNoLogs = taskDtoBuilderService.createDtoFromEntityWithoutProcessEvents(task);
        if (withStatusUpdate) {
            streamBridge.send(TASK_STATUS_UPDATED_BINDING, taskDtoNoLogs);
        }
        if (withNewInput) {
            streamBridge.send(TASK_INPUT_UPDATED_BINDING, taskDtoNoLogs);
        }
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        final String websocketNotifyUpdateBaseUrl = websocketConfig.getNotify() + "/update/";
        stompBridge.convertAndSend(websocketNotifyUpdateBaseUrl + fmt.format(task.getTimestamp()), taskDtoNoLogs); // to actualize status/files in the timestamp view
        stompBridge.convertAndSend(websocketNotifyUpdateBaseUrl + fmt.format(task.getTimestamp()).substring(0, 10), taskDtoNoLogs); // to actualize status/files in the business date view
        if (withEventsUpdate) {
            stompBridge.convertAndSend(websocketNotifyUpdateBaseUrl + fmt.format(task.getTimestamp()) + "/events", true); // to actualize event logs in the timestamp view
        }
    }

    public void notify(Set<TaskWithStatusUpdate> taskWithStatusUpdateSet) {
        taskWithStatusUpdateSet.parallelStream().forEach(t -> notify(t.getTask(), t.isStatusUpdated(), true));
    }

    public void notifyNewInput(Set<TaskWithStatusUpdate> taskWithStatusUpdateSet) {
        taskWithStatusUpdateSet.parallelStream().forEach(t -> notify(t.getTask(), t.isStatusUpdated(), true, true));
    }
}
