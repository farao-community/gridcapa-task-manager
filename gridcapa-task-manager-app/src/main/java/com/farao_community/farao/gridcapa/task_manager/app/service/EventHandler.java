/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskLogEventUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
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
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@Service
public class EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandler.class);
    private static final Object LOCK = new Object();

    private final TaskRepository taskRepository;
    private final TaskUpdateNotifier taskUpdateNotifier;

    public EventHandler(TaskRepository taskRepository, TaskUpdateNotifier taskUpdateNotifier) {
        this.taskRepository = taskRepository;
        this.taskUpdateNotifier = taskUpdateNotifier;
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
                    task.addProcessEvent(offsetDateTime, loggerEvent.getLevel(), message, loggerEvent.getServiceName());
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
