/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskLogEventUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties.TASK_MANAGER_LOCK;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Service
public class EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandler.class);
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final TaskUpdateNotifier taskUpdateNotifier;

    public EventHandler(TaskRepository taskRepository, TaskService taskService, TaskUpdateNotifier taskUpdateNotifier) {
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.taskUpdateNotifier = taskUpdateNotifier;
    }

    @Bean
    public Consumer<Flux<List<byte[]>>> consumeTaskEventUpdate() {
        return f -> f.subscribe(messages -> {
            try {
                handleTaskEventBatchUpdate(mapMessagesToListEvents(messages));
            } catch (Exception e) {
                LOGGER.error(String.format("Unable to handle task events update properly %s", messages), e);
            }
        });
    }

    List<TaskLogEventUpdate> mapMessagesToListEvents(List<byte[]> messages) {
        return messages.stream()
            .map(String::new)
            .map(this::mapMessageToEvent)
            .filter(Objects::nonNull)
            .toList();
    }

    TaskLogEventUpdate mapMessageToEvent(String messages) {
        try {
            return new ObjectMapper().readValue(messages, TaskLogEventUpdate.class);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Couldn't parse log event, Impossible to match the event with concerned task", e);
            return null;
        }
    }

    void handleTaskEventBatchUpdate(List<TaskLogEventUpdate> events) {
        synchronized (TASK_MANAGER_LOCK) {
            Map<UUID, Task> storedTasks = new HashMap<>();
            for (TaskLogEventUpdate event : events) {
                UUID taskUUID = UUID.fromString(event.getId());
                Task task = storedTasks.get(taskUUID);
                if (task == null) {
                    Optional<Task> optionalTask = taskRepository.findByIdAndFetchProcessFiles(UUID.fromString(event.getId()));
                    if (optionalTask.isPresent()) {
                        task = optionalTask.get();
                        storedTasks.put(taskUUID, task);
                        taskService.addProcessEventToTask(event, task);
                    } else {
                        LOGGER.warn("Task {} does not exist. Impossible to update task with log event", event.getId());
                    }
                } else {
                    taskService.addProcessEventToTask(event, task);
                }
            }
            Collection<Task> tasksToSave = storedTasks.values();
            taskRepository.saveAll(tasksToSave);
            for (Task task : tasksToSave) {
                taskUpdateNotifier.notify(task, false, true);
                LOGGER.debug("Task events have been added on {}", task.getTimestamp());
            }
        }
    }
}
