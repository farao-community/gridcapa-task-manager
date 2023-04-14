/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.time.OffsetDateTime;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.RUNNING;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@SpringBootTest(properties = {"purge-task-events.nb-days=7", "purge-task-events.cron=0 0 12 * * *"})
class StatusHandlerTest {

    @Autowired
    private TaskUpdateNotifier taskUpdateNotifier;

    @MockBean
    private StreamBridge streamBridge; // Useful to avoid AMQP connection that would fail

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private StatusHandler statusHandler;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
    }

    @Test
    void handleTaskStatusUpdateFromMessagesTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        taskRepository.save(task);

        statusHandler.handleTaskStatusUpdate(new TaskStatusUpdate(task.getId(), RUNNING));

        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(RUNNING, updatedTask.getStatus());
    }

    @Test
    void handleTaskStatusUpdateFromApiTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        taskRepository.save(task);

        statusHandler.handleTaskStatusUpdate(taskTimestamp, RUNNING);

        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(RUNNING, updatedTask.getStatus());
    }
}
