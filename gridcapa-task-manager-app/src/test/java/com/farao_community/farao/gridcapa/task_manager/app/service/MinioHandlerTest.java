/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.TaskManagerTestUtil;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessFileRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import io.minio.messages.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class MinioHandlerTest {
    private static final String INPUT_FILE_GROUP_VALUE = MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE;

    @Autowired
    private MinioHandler minioHandler;

    @Autowired
    private ProcessFileRepository processFileRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockBean
    private MinioAdapter minioAdapter;

    @MockBean
    private TaskUpdateNotifier taskUpdateNotifier; // Useful to avoid AMQP connection that would fail

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        processFileRepository.deleteAll();
    }

    @Test
    void testUpdate() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "CSE/D2CC/CGMs/cgm-test";
        Event event = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        minioHandler.updateTasks(event);

        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isPresent());
        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(cgmUrl, task.getInput("CGM").orElseThrow().getFileUrl());
        assertEquals("cgm-test", task.getInput("CGM").orElseThrow().getFilename());
    }

    @Test
    void testUpdateWithTwoFileTypesInTheSameTimestamp() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "CSE/D2CC/CGMs/cgm-test";
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cracUrl = "CSE/D2CC/CRACs/crac-test";
        Event eventCrac = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cracUrl);

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCrac);

        assertEquals(1, taskRepository.findAll().size());
        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isPresent());
        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals(cgmUrl, task.getInput("CGM").orElseThrow().getFileUrl());
        assertEquals(cracUrl, task.getInput("CRAC").orElseThrow().getFileUrl());
        assertEquals("cgm-test", task.getInput("CGM").orElseThrow().getFilename());
        assertEquals("crac-test", task.getInput("CRAC").orElseThrow().getFilename());
    }

    @Test
    void testUpdateWithNotHandledProcess() {
        testTimeInterval("CSE_IDCC", "2021-09-30T23:00Z/2021-10-01T00:00Z", 0);
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
        String cgmUrl = "cgmUrl";
        Event event = TaskManagerTestUtil.createEvent(minioAdapter, process, INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", interval, cgmUrl);

        minioHandler.updateTasks(event);

        assertEquals(expectedFileNumber, taskRepository.findAll().size());
    }

    @Test
    void checkStatusUpdateToReady() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cracUrl);

        minioHandler.updateTasks(eventCgm);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
        minioHandler.updateTasks(eventCrac);
        assertEquals(READY, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void checkStatusUpdateBackToCreated() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cracUrl);

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCrac);
        minioHandler.removeProcessFile(eventCrac);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void testCreationEventsForTwoFilesWithDifferentTypesAndSameTs() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        String cgmUrl = "cgmUrl";
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cracUrl);

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
        String cgmUrl = "cgmUrl";
        Event eventCgm = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrl);

        String cgmUrlNew = "cgmUrlNew";
        Event eventCgmNew = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-new-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", cgmUrlNew);

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
                "cgmUrl",
                OffsetDateTime.now());

        task.addProcessEvent(OffsetDateTime.now(), "INFO", "Crac available");
        task.addProcessFile(
                "CSE/D2CC/CRACs/crac-test",
                MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "CRAC",
                OffsetDateTime.parse("2021-10-01T21:00Z"),
                OffsetDateTime.parse("2021-10-01T22:00Z"),
                "cracUrl",
                OffsetDateTime.now());

        taskRepository.save(task);

        Event eventCracDeletion = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", "cracUrl");
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
                "cracUrl",
                OffsetDateTime.now());
        taskRepository.save(task);

        Event eventCracDeletion = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z", "cracUrl");
        minioHandler.removeProcessFile(eventCracDeletion);

        assertEquals(NOT_CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void checkCorrectFileIsUsedWhenIntervalsOverlap() {
        OffsetDateTime taskTimeStampInInterval1 = OffsetDateTime.of(2020, 2, 1, 0, 30, 0, 0, ZoneOffset.UTC);
        OffsetDateTime taskTimeStampInInterval2 = OffsetDateTime.of(2021, 2, 1, 0, 30, 0, 0, ZoneOffset.UTC);
        OffsetDateTime taskTimeStampInBothIntervals = OffsetDateTime.of(2020, 8, 1, 0, 30, 0, 0, ZoneOffset.UTC);

        Event eventFileInterval1 = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "File", "File-1",
                "2020-01-01T22:30Z/2020-12-31T22:30Z", "File-1-url");

        Event eventFileInterval2 = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "File", "File-2",
                "2020-06-01T22:30Z/2021-06-30T22:30Z", "File-2-url");

        minioHandler.updateTasks(eventFileInterval1);
        minioHandler.updateTasks(eventFileInterval2);

        Task taskInterval1 = taskRepository.findByTimestamp(taskTimeStampInInterval1).orElseThrow();
        Task taskInterval2 = taskRepository.findByTimestamp(taskTimeStampInInterval2).orElseThrow();
        Task taskHavingFileWithOverlappingIntervals = taskRepository.findByTimestamp(taskTimeStampInBothIntervals).orElseThrow();

        assertEquals("File-1", taskInterval1.getInput("File").get().getFilename());
        assertEquals("File-2", taskInterval2.getInput("File").get().getFilename());
        assertEquals("File-2", taskHavingFileWithOverlappingIntervals.getInput("File").get().getFilename());

        Event eventFile2Deletion = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "File", "File-2", "2020-06-01T22:30Z/2021-06-30T22:30Z", "File-2-url");
        minioHandler.removeProcessFile(eventFile2Deletion);
        taskHavingFileWithOverlappingIntervals = taskRepository.findByTimestamp(taskTimeStampInBothIntervals).orElseThrow();

        assertEquals("File-1", taskHavingFileWithOverlappingIntervals.getInput("File").get().getFilename());
    }
}
