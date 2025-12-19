/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Consumer;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.RUNNING;
import static com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties.TASK_MANAGER_LOCK;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@Service
public class StatusHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusHandler.class);

    private final MinioHandler minioHandler;
    private final TaskRepository taskRepository;
    private final TaskUpdateNotifier taskUpdateNotifier;
    private final Logger businessLogger;

    public StatusHandler(final MinioHandler minioHandler,
                         final TaskRepository taskRepository,
                         final TaskUpdateNotifier taskUpdateNotifier,
                         final Logger businessLogger) {
        this.minioHandler = minioHandler;
        this.taskRepository = taskRepository;
        this.taskUpdateNotifier = taskUpdateNotifier;
        this.businessLogger = businessLogger;
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

    public void handleTaskStatusUpdate(final TaskStatusUpdate taskStatusUpdate) {
        synchronized (TASK_MANAGER_LOCK) { // use same lock to avoid parallel handling between status update and minioHandler
            final Optional<Task> optionalTask = taskRepository.findByIdAndFetchProcessFiles(taskStatusUpdate.getId());
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatusUpdate.getTaskStatus());
                LOGGER.info("Receiving task status update for task id {} with status {}", taskStatusUpdate.getId(), taskStatusUpdate.getTaskStatus());
                if (taskStatusUpdate.getTaskStatus().isOver()) {
                    minioHandler.emptyWaitingList(optionalTask.get().getTimestamp());
                }
            } else {
                LOGGER.warn("Task {} does not exist. Impossible to update status", taskStatusUpdate.getId());
            }
        }
    }

    public Optional<Task> handleTaskStatusUpdate(final OffsetDateTime timestamp, final TaskStatus taskStatus) {
        synchronized (TASK_MANAGER_LOCK) {
            final Optional<Task> optionalTask = taskRepository.findByTimestamp(timestamp);
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

    private void updateTaskStatus(final Task task,
                                  final TaskStatus taskStatus) {
        task.setStatus(taskStatus);
        final Task savedTask = taskRepository.saveAndFlush(task);
        taskUpdateNotifier.notify(savedTask, true, false);
        LOGGER.info("Task status has been updated on {} to {}", task.getTimestamp(), savedTask.getStatus());
        if (taskStatus.isOver() || task.getStatus() == RUNNING) {
            MDC.put("gridcapa-task-id", task.getId().toString());
            businessLogger.info("Task status has been updated to {}.", task.getStatus());
        }
    }
}
