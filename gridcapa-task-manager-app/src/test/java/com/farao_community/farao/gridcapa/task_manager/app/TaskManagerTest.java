/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessFileRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.RUNNING;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class TaskManagerTest {

    @Autowired
    private TaskUpdateNotifier taskUpdateNotifier;

    @MockBean
    private StreamBridge streamBridge; // Useful to avoid AMQP connection that would fail

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProcessFileRepository processFileRepository;

    @Autowired
    private TaskManager taskManager;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        processFileRepository.deleteAll();
    }

    @Test
    void handleTaskLogEventUpdateTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        task.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f6"));
        taskRepository.save(task);
        String logEvent = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f6\",\n" +
                "  \"timestamp\": \"2021-12-30T17:31:33.030+01:00\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        taskManager.handleTaskEventUpdate(logEvent);
        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(1, updatedTask.getProcessEvents().size());
        ProcessEvent event = updatedTask.getProcessEvents().iterator().next();
        assertEquals(OffsetDateTime.parse("2021-12-30T16:31:33.030Z"), event.getTimestamp());
        assertEquals("INFO", event.getLevel());
        assertEquals("Hello from backend", event.getMessage());
    }

    @Test
    void handleTaskLogEventUpdateWithPrefixTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        task.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f6"));
        taskRepository.save(task);
        String logEvent = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f6\",\n" +
                "  \"timestamp\": \"2021-12-30T17:31:33.030+01:00\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" ,\n" +
                "  \"eventPrefix\": \"STEP-1\" \n" +
                "}";
        taskManager.handleTaskEventUpdate(logEvent);
        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(1, updatedTask.getProcessEvents().size());
        ProcessEvent event = updatedTask.getProcessEvents().iterator().next();
        assertEquals(OffsetDateTime.parse("2021-12-30T16:31:33.030Z"), event.getTimestamp());
        assertEquals("INFO", event.getLevel());
        assertEquals("[STEP-1] : Hello from backend", event.getMessage());
    }

    @Test
    void handleTaskStatusUpdateFromMessagesTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        taskRepository.save(task);

        taskManager.handleTaskStatusUpdate(new TaskStatusUpdate(task.getId(), RUNNING));

        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(RUNNING, updatedTask.getStatus());
    }

    @Test
    void handleTaskStatusUpdateFromApiTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        taskRepository.save(task);

        taskManager.handleTaskStatusUpdate(taskTimestamp, RUNNING);

        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(RUNNING, updatedTask.getStatus());
    }
}
