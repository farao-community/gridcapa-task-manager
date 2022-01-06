/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import io.minio.messages.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskManagerTest {

    @MockBean
    private TaskUpdateNotifier taskUpdateNotifier; // Useful to avoid AMQP connection that would fail

    @MockBean
    private MinioAdapter minioAdapter;

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

    private Event createEvent(String processTag, String fileType, String fileKey, String validityInterval, String fileUrl) {
        Event event = Mockito.mock(Event.class);
        Map<String, String> metadata = Map.of(
            TaskManager.FILE_PROCESS_TAG, processTag,
            TaskManager.FILE_TYPE, fileType,
            TaskManager.FILE_VALIDITY_INTERVAL, validityInterval
        );
        Mockito.when(event.userMetadata()).thenReturn(metadata);
        Mockito.when(minioAdapter.generatePreSignedUrl(event)).thenReturn(fileUrl);
        Mockito.when(event.objectName()).thenReturn(fileKey);
        return event;
    }

    @Test
    void testUpdate() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        taskManager.updateTasks(event);

        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isPresent());
        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(cgmUrl, task.getProcessFile("CGM").orElseThrow().getFileUrl());
        assertEquals("cgm-test", task.getProcessFile("CGM").orElseThrow().getFilename());
    }

    @Test
    void testUpdateWithTwoFileTypesInTheSameTimestamp() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cracUrl);

        taskManager.updateTasks(eventCgm);
        taskManager.updateTasks(eventCrac);

        assertEquals(1, taskRepository.findAll().size());
        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isPresent());
        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(cgmUrl, task.getProcessFile("CGM").orElseThrow().getFileUrl());
        assertEquals(cracUrl, task.getProcessFile("CRAC").orElseThrow().getFileUrl());
        assertEquals("cgm-test", task.getProcessFile("CGM").orElseThrow().getFilename());
        assertEquals("crac-test", task.getProcessFile("CRAC").orElseThrow().getFilename());
    }

    @Test
    void testUpdateWithNotHandledProcess() {
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_IDCC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T23:00Z/2021-10-01T00:00Z", cgmUrl);

        taskManager.updateTasks(event);

        assertEquals(0, taskRepository.findAll().size());
    }

    @Test
    void testUpdateWithNotHandledFileType() {
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "GLSK", "CSE/D2CC/GLSKs/glsk-test", "2021-09-30T23:00Z/2021-10-01T00:00Z", cgmUrl);

        taskManager.updateTasks(event);

        assertEquals(0, taskRepository.findAll().size());
    }

    @Test
    void testUpdateWithDailyFile() {
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T22:00Z/2021-10-01T22:00Z", cgmUrl);

        taskManager.updateTasks(event);

        assertEquals(24, taskRepository.findAll().size());
    }

    @Test
    void checkStatusUpdateToReady() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cracUrl);

        taskManager.updateTasks(eventCgm);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
        taskManager.updateTasks(eventCrac);
        assertEquals(READY, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void checkStatusUpdateBackToCreated() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cracUrl);

        taskManager.updateTasks(eventCgm);
        taskManager.updateTasks(eventCrac);
        taskManager.removeProcessFile(eventCrac);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void testCreationEventsForTwoFilesWithDifferentTypesAndSameTs() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cracUrl);

        taskManager.updateTasks(eventCgm);
        taskManager.updateTasks(eventCrac);

        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(2, task.getProcessEvents().size());
        Iterator<ProcessEvent> processEventIterator = task.getProcessEvents().iterator();
        ProcessEvent event = processEventIterator.next();
        assertEquals("INFO", event.getLevel());
        assertEquals("The CGM : 'cgm-test' is available", event.getMessage());
        event = processEventIterator.next();
        assertEquals("INFO", event.getLevel());
        assertEquals("The CRAC : 'crac-test' is available", event.getMessage());
    }

    @Test
    void testUpdateEventsForTwoFilesWithSameTypeAndSameTs() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cgmUrlNew = "cgmUrlNew";
        Event eventCgmNew = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-new-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrlNew);

        taskManager.updateTasks(eventCgm);
        taskManager.updateTasks(eventCgmNew);

        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(2, task.getProcessEvents().size());
        Iterator<ProcessEvent> processEventIterator = task.getProcessEvents().iterator();
        ProcessEvent event1 = processEventIterator.next();
        ProcessEvent event2 = processEventIterator.next();
        assertTrue(event1.getTimestamp().isBefore(event2.getTimestamp()));
        assertEquals("INFO", event1.getLevel());
        assertEquals("INFO", event2.getLevel());
        assertEquals("The CGM : 'cgm-test' is available", event1.getMessage());
        assertEquals("A new version of CGM is available : 'cgm-new-test'", event2.getMessage());
    }

    @Test
    void testDeletionEventsOfTaskWithTwoDifferentFileTypes() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z").withOffsetSameInstant(ZoneOffset.UTC);
        Task task = new Task(taskTimestamp);

        task.addProcessEvent(OffsetDateTime.now(), "INFO", "CGM available");
        task.addProcessFile(
            "CSE/D2CC/CGMs/cgm-test",
            "CGM",
            OffsetDateTime.parse("2021-10-01T21:00Z"),
            OffsetDateTime.parse("2021-10-01T22:00Z"),
            "cgmUrl",
            OffsetDateTime.now());

        task.addProcessEvent(OffsetDateTime.now(), "INFO", "Crac available");
        task.addProcessFile(
            "CSE/D2CC/CRACs/crac-test",
            "CRAC",
            OffsetDateTime.parse("2021-10-01T21:00Z"),
            OffsetDateTime.parse("2021-10-01T22:00Z"),
            "cracUrl",
            OffsetDateTime.now());

        taskRepository.save(task);

        Event eventCracDeletion = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", "cracUrl");
        taskManager.removeProcessFile(eventCracDeletion);

        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(3, updatedTask.getProcessEvents().size());

        Iterator<ProcessEvent> processEventIterator = updatedTask.getProcessEvents().iterator();
        processEventIterator.next();
        processEventIterator.next();
        ProcessEvent event = processEventIterator.next();
        assertEquals("The CRAC : 'crac-test' is deleted", event.getMessage());
        assertEquals(1, updatedTask.getProcessFiles().size());
    }

    @Test
    void testDeletionEventsWithTaskDeletion() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        task.addProcessEvent(OffsetDateTime.now(), "INFO", "Crac available");
        task.addProcessFile(
            "CSE/D2CC/CRACs/crac-test",
            "CRAC",
            OffsetDateTime.parse("2021-10-01T21:00Z"),
            OffsetDateTime.parse("2021-10-01T22:00Z"),
            "cracUrl",
            OffsetDateTime.now());
        taskRepository.save(task);

        Event eventCracDeletion = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", "cracUrl");
        taskManager.removeProcessFile(eventCracDeletion);

        assertEquals(NOT_CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
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
        taskManager.handleTaskLogEventUpdate().accept(logEvent);
        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(1, updatedTask.getProcessEvents().size());
        ProcessEvent event = updatedTask.getProcessEvents().iterator().next();
        assertEquals(OffsetDateTime.parse("2021-12-30T16:31:33Z"), event.getTimestamp());
        assertEquals("INFO", event.getLevel());
        assertEquals("Hello from backend", event.getMessage());
    }
}
