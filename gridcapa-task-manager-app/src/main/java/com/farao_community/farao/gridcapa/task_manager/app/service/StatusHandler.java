/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
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

import static com.farao_community.farao.gridcapa.task_manager.app.service.MinioHandler.LOCK_MINIO_AND_STATUS_HANDLER;

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
        synchronized (LOCK_MINIO_AND_STATUS_HANDLER) { // use same lock to avoid parallel handling between status update and minioHandler
            Optional<Task> optionalTask = taskRepository.findByIdWithProcessFiles(taskStatusUpdate.getId());
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatusUpdate.getTaskStatus());
                LOGGER.info("Receiving task status update for task id {} with status {}", taskStatusUpdate.getId(), taskStatusUpdate.getTaskStatus());
                if (isTaskOver(taskStatusUpdate.getTaskStatus())) {
                    minioHandler.emptyWaitingList(optionalTask.get().getTimestamp());

                }
            } else {
                LOGGER.warn("Task {} does not exist. Impossible to update status", taskStatusUpdate.getId());
            }
        }
    }

    public Optional<Task> handleTaskStatusUpdate(OffsetDateTime timestamp, TaskStatus taskStatus) {
        synchronized (LOCK_MINIO_AND_STATUS_HANDLER) {
            Optional<Task> optionalTask = taskRepository.findByTimestamp(timestamp);
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatus);
                if (isTaskOver(taskStatus)) {
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
        Task savedTask = taskRepository.saveAndFlush(task);
        taskUpdateNotifier.notify(savedTask, true);
        LOGGER.info("Task status has been updated on {} to {}", task.getTimestamp(), savedTask.getStatus());
    }

    private boolean isTaskOver(TaskStatus taskStatus) {
        return taskStatus.equals(TaskStatus.SUCCESS) ||
                taskStatus.equals(TaskStatus.INTERRUPTED) ||
                taskStatus.equals(TaskStatus.ERROR);
    }
}
