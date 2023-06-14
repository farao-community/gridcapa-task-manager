/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.TaskManagerApplication;
import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@Service
public class StatusHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusHandler.class);

    private final MinioHandler minioHandler;
    private final TaskRepository taskRepository;
    private final TaskUpdateNotifier taskUpdateNotifier;

    public StatusHandler(MinioHandler minioHandler, TaskRepository taskRepository, TaskUpdateNotifier taskUpdateNotifier) {
        this.minioHandler = minioHandler;
        this.taskRepository = taskRepository;
        this.taskUpdateNotifier = taskUpdateNotifier;
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
        LOGGER.warn("updating status for task {} to {}", taskStatusUpdate.getId(), taskStatusUpdate.getTaskStatus());
        synchronized (TaskManagerApplication.LOCK) {
            LOGGER.warn("actually updating status for task {} to {}", taskStatusUpdate.getId(), taskStatusUpdate.getTaskStatus());
            Optional<Task> optionalTask = taskRepository.findByIdWithProcessFiles(taskStatusUpdate.getId());
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatusUpdate.getTaskStatus());
                if (taskStatusUpdate.getTaskStatus().isOver()) {
                    minioHandler.emptyWaitingList(optionalTask.get().getTimestamp());
                }
            } else {
                LOGGER.warn("Task {} does not exist. Impossible to update status", taskStatusUpdate.getId());
            }
        }
    }

    public Optional<Task> handleTaskStatusUpdate(OffsetDateTime timestamp, TaskStatus taskStatus) {
        synchronized (TaskManagerApplication.LOCK) {
            Optional<Task> optionalTask = taskRepository.findByTimestamp(timestamp);
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatus);
                if (taskStatus.isOver()) {
                    minioHandler.emptyWaitingList(timestamp);
                }
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
}
