/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.*;
import com.farao_community.farao.gridcapa.task_manager.app.entities.*;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import io.minio.messages.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@SpringBootTest
class MinioHandlerTest {
    private static final String INPUT_FILE_GROUP_VALUE = MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE;

    @Autowired
    private TaskUpdateNotifier taskUpdateNotifier;

    @MockBean
    private StreamBridge streamBridge; // Useful to avoid AMQP connection that would fail

    @MockBean
    private MinioAdapter minioAdapter;

    @Autowired
    private ProcessFileRepository processFileRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MinioHandler minioHandler;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        processFileRepository.deleteAll();
    }

    @Test
    void testUpdate() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event event = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(event);

        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isPresent());
        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals("CSE/D2CC/CGMs/cgm-test", task.getInput("CGM").orElseThrow().getFileObjectKey());
        assertEquals("cgm-test", task.getInput("CGM").orElseThrow().getFilename());
    }

    @Test
    void testUpdateWithTwoFileTypesInTheSameTimestamp() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        Event eventCrac = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCrac);

        assertEquals(1, taskRepository.findAll().size());
        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isPresent());
        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals("CSE/D2CC/CGMs/cgm-test", task.getInput("CGM").orElseThrow().getFileObjectKey());
        assertEquals("CSE/D2CC/CRACs/crac-test", task.getInput("CRAC").orElseThrow().getFileObjectKey());
        assertEquals("cgm-test", task.getInput("CGM").orElseThrow().getFilename());
        assertEquals("crac-test", task.getInput("CRAC").orElseThrow().getFilename());
    }

    @Test
    void testUpdateWithEmptyIntervalFileType() {
        testTimeInterval("CSE_D2CC", "", 0);
    }

    @Test
    void testUpdateWithDailyFile() {
        testTimeInterval("CSE_D2CC", "2021-09-30T22:00Z/2021-10-01T22:00Z", 24);
    }

    private void testTimeInterval(String process, String interval, int expectedFileNumber) {
        Event event = TaskManagerTestUtil.createEvent(minioAdapter, process, INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", interval);

        minioHandler.updateTasks(event);

        assertEquals(expectedFileNumber, taskRepository.findAll().size());
    }

    @Test
    void checkStatusUpdateToReady() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        Event eventCrac = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
        minioHandler.updateTasks(eventCrac);
        assertEquals(READY, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void checkStatusUpdateBackToCreated() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        Event eventCrac = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCrac);
        minioHandler.removeProcessFile(eventCrac);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void testCreationEventsForTwoFilesWithDifferentTypesAndSameTs() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        Event eventCrac = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCrac);

        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(2, task.getProcessEvents().size());
        Iterator<ProcessEvent> processEventIterator = task.getProcessEvents().iterator();
        ProcessEvent event = processEventIterator.next();
        assertEquals("INFO", event.getLevel());
        assertEquals("The CRAC : 'crac-test' is available", event.getMessage());
        event = processEventIterator.next();
        assertEquals("INFO", event.getLevel());
        assertEquals("The CGM : 'cgm-test' is available", event.getMessage());
    }

    @Test
    void testUpdateEventsForTwoFilesWithSameTypeAndSameTs() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        Event eventCgmNew = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-new-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCgmNew);

        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(2, task.getProcessEvents().size());
        Iterator<ProcessEvent> processEventIterator = task.getProcessEvents().iterator();
        ProcessEvent event1 = processEventIterator.next();
        ProcessEvent event2 = processEventIterator.next();
        assertTrue(event2.getTimestamp().isBefore(event1.getTimestamp()));
        assertEquals("INFO", event1.getLevel());
        assertEquals("INFO", event2.getLevel());
        assertEquals("The CGM : 'cgm-test' is available", event2.getMessage());
        assertEquals("A new version of CGM is available : 'cgm-new-test'", event1.getMessage());
    }

    @Test
    void testDeletionEventsOfTaskWithTwoDifferentFileTypes() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z").withOffsetSameInstant(ZoneOffset.UTC);
        Task task = new Task(taskTimestamp);

        task.addProcessEvent(OffsetDateTime.now(), "INFO", "CGM available");
        task.addProcessFile(
                "CSE/D2CC/CGMs/cgm-test",
                MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "CGM",
                OffsetDateTime.parse("2021-10-01T21:00Z"),
                OffsetDateTime.parse("2021-10-01T22:00Z"),
                OffsetDateTime.now());

        task.addProcessEvent(OffsetDateTime.now(), "INFO", "Crac available");
        task.addProcessFile(
                "CSE/D2CC/CRACs/crac-test",
                MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "CRAC",
                OffsetDateTime.parse("2021-10-01T21:00Z"),
                OffsetDateTime.parse("2021-10-01T22:00Z"),
                OffsetDateTime.now());

        taskRepository.save(task);

        Event eventCracDeletion = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        minioHandler.removeProcessFile(eventCracDeletion);

        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(3, updatedTask.getProcessEvents().size());

        Iterator<ProcessEvent> processEventIterator = updatedTask.getProcessEvents().iterator();
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
                MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "CRAC",
                OffsetDateTime.parse("2021-10-01T21:00Z"),
                OffsetDateTime.parse("2021-10-01T22:00Z"),
                OffsetDateTime.now());
        taskRepository.save(task);

        Event eventCracDeletion = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        minioHandler.removeProcessFile(eventCracDeletion);

        assertEquals(NOT_CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void testCheckIfAFileWithSameTypeAlreadyExist() {
        Map<ProcessFileMinio, List<OffsetDateTime>> mapWaitingFiles = new HashMap<>();
        minioHandler.setMapWaitingFiles(mapWaitingFiles);
        OffsetDateTime timestamp1 = OffsetDateTime.now();
        OffsetDateTime timestamp2 = OffsetDateTime.now().plusHours(1);
        List<OffsetDateTime> timestamps = Arrays.asList(timestamp1, timestamp2);

        Task task1 = mock(Task.class);
        Task task2 = mock(Task.class);
        when(task1.getTimestamp()).thenReturn(timestamp1);
        when(task2.getTimestamp()).thenReturn(timestamp2);

        ProcessFile processFile = new ProcessFile("", "", "type", null, null, null);
        minioHandler.removeFileWithSameTypeAndTimestampsFromWaitingFiles(processFile, timestamps);
        ProcessFileMinio processFileMinio = new ProcessFileMinio(new ProcessFile("", "", "type", null, null, null), null);
        mapWaitingFiles.put(processFileMinio, timestamps);
        minioHandler.removeFileWithSameTypeAndTimestampsFromWaitingFiles(processFile, timestamps);
        assertTrue(mapWaitingFiles.isEmpty());
    }

    @Test
    void testEmptyWaitingList() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Map<ProcessFileMinio, List<OffsetDateTime>> mapFiles = new HashMap<>();

        ProcessFile processFile = new ProcessFile("", "", "type", timestamp.minusHours(1), timestamp.plusHours(1), null);
        ProcessFileMinio processFileMinio = new ProcessFileMinio(processFile, FileEventType.AVAILABLE);
        mapFiles.put(processFileMinio, Collections.singletonList(timestamp));

        minioHandler.setMapWaitingFiles(mapFiles);
        minioHandler.emptyWaitingList(timestamp);
    }
}
