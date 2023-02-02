/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskLogEventUpdate;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);
    private static final Object LOCK = new Object();

    private final TaskUpdateNotifier taskUpdateNotifier;
    private final TaskRepository taskRepository;

    public TaskManager(TaskUpdateNotifier taskUpdateNotifier,
                       TaskRepository taskRepository) {
        this.taskUpdateNotifier = taskUpdateNotifier;
        this.taskRepository = taskRepository;
    }

    @Bean
    public Consumer<Flux<String>> consumeTaskEventUpdate() {
        return f -> f.subscribe(event -> {
            try {
                handleTaskEventUpdate(event);
            } catch (Exception e) {
                LOGGER.error(String.format("Unable to handle task event update properly %s", event), e);
            }
        });
    }

    @Bean
    public Consumer<Flux<TaskStatusUpdate>> consumeTaskStatusUpdate() {
        return f -> f.subscribe(taskStatusUpdate -> {
            try {
                handleTaskStatusUpdate(taskStatusUpdate);
            } catch (Exception e) {
                LOGGER.error(String.format("Unable to handle task status update properly %s", taskStatusUpdate), e);
            }
        });
    }

    public void handleTaskStatusUpdate(TaskStatusUpdate taskStatusUpdate) {
        synchronized (LOCK) {
            Optional<Task> optionalTask = taskRepository.findByIdWithProcessFiles(taskStatusUpdate.getId());
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatusUpdate.getTaskStatus());
            } else {
                LOGGER.warn("Task {} does not exist. Impossible to update status", taskStatusUpdate.getId());
            }
        }
    }

    public Optional<Task> handleTaskStatusUpdate(OffsetDateTime timestamp, TaskStatus taskStatus) {
        synchronized (LOCK) {
            Optional<Task> optionalTask = taskRepository.findByTimestamp(timestamp);
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatus);
                return optionalTask;
            } else {
                LOGGER.warn("Task at {} does not exist. Impossible to update status", timestamp);
                return Optional.empty();
            }
        }
    }

    private void updateTaskStatus(Task task, TaskStatus taskStatus) {
        task.setStatus(taskStatus);
        taskRepository.saveAndFlush(task);
        taskUpdateNotifier.notify(task, true);
        LOGGER.debug("Task status has been updated on {} to {}", task.getTimestamp(), taskStatus);
    }

    void handleTaskEventUpdate(String loggerEventString) {
        synchronized (LOCK) {
            try {
                TaskLogEventUpdate loggerEvent = new ObjectMapper().readValue(loggerEventString, TaskLogEventUpdate.class);
                Optional<Task> optionalTask = taskRepository.findByIdWithProcessFiles(UUID.fromString(loggerEvent.getId()));
                if (optionalTask.isPresent()) {
                    Task task = optionalTask.get();
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(loggerEvent.getTimestamp());
                    String message = loggerEvent.getMessage();
                    Optional<String> optionalEventPrefix = loggerEvent.getEventPrefix();
                    if (optionalEventPrefix.isPresent()) {
                        message = "[" + optionalEventPrefix.get() + "] : " + loggerEvent.getMessage();
                    }
                    task.addProcessEvent(offsetDateTime, loggerEvent.getLevel(), message);
                    taskRepository.save(task);
                    taskUpdateNotifier.notify(task, false);
                    LOGGER.debug("Task event has been added on {} provided by {}", task.getTimestamp(), loggerEvent.getServiceName());
                } else {
                    LOGGER.warn("Task {} does not exist. Impossible to update task with log event", loggerEvent.getId());
                }
            } catch (JsonProcessingException e) {
                LOGGER.warn("Couldn't parse log event, Impossible to match the event with concerned task", e);
            }
        }
    }
}
