/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.TaskManagerTestUtil;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.FileEventType;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFileMinio;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessEventRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessFileRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import io.minio.messages.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.CREATED;
import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.NOT_CREATED;
import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.READY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private ProcessEventRepository processEventRepository;

    @Autowired
    private MinioHandler minioHandler;
    @Autowired
    private CompositeMeterRegistryAutoConfiguration compositeMeterRegistryAutoConfiguration;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        processFileRepository.deleteAll();
    }

    @Test
    void testUpdate() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event event = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "documentIdCgm", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(event);

        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isPresent());
        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        assertEquals("CSE/D2CC/CGMs/cgm-test", task.getInput("CGM").orElseThrow().getFileObjectKey());
        assertEquals("cgm-test", task.getInput("CGM").orElseThrow().getFilename());
        assertEquals("documentIdCgm", task.getProcessFiles().first().getDocumentId());
    }

    @Test
    void testUpdateOnRunningTask() {
        final OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        final OffsetDateTime taskTimestampEnd = OffsetDateTime.parse("2021-09-30T22:00Z");
        final Event event = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "documentIdCgm", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        final Task runningTask = new Task(taskTimestamp);
        runningTask.setStatus(TaskStatus.RUNNING);
        final ProcessFile pf = new ProcessFile("", "", "", "", taskTimestamp, taskTimestampEnd, taskTimestamp);
        runningTask.addProcessFile(pf);
        taskRepository.save(runningTask);
        minioHandler.updateTasks(event);

        final ProcessEvent processEvent = processEventRepository.findAll().get(0);
        Assertions.assertEquals("A new version of CGM is waiting for process to end to be available : 'cgm-test'", processEvent.getMessage());
        Assertions.assertEquals("WARN", processEvent.getLevel());
        Assertions.assertEquals("task-manager", processEvent.getServiceName());
    }

    @Test
    void testUpdateWithTwoFileTypesInTheSameTimestamp() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event eventCgm = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "documentIdCgm", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        Event eventCrac = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "documentIdCrac", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCrac);

        assertEquals(1, taskRepository.findAll().size());
        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isPresent());
        Task task = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        ProcessFile processFileCgm = task.getInput("CGM").orElseThrow();
        assertEquals("CSE/D2CC/CGMs/cgm-test", processFileCgm.getFileObjectKey());
        ProcessFile processFileCrac = task.getInput("CRAC").orElseThrow();
        assertEquals("CSE/D2CC/CRACs/crac-test", processFileCrac.getFileObjectKey());
        assertEquals("cgm-test", processFileCgm.getFilename());
        assertEquals("crac-test", processFileCrac.getFilename());
        assertEquals("documentIdCgm", processFileCgm.getDocumentId());
        assertEquals("documentIdCrac", processFileCrac.getDocumentId());
    }

    @ParameterizedTest
    @CsvSource({",0", "2021-09-30T22:00Z/2021-10-01T22:00Z,24"})
        // First test with empty interval ; Second test with daily file
    void testTimeInterval(String interval, int expectedFileNumber) {
        Event event = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", interval);

        minioHandler.updateTasks(event);

        assertEquals(expectedFileNumber, taskRepository.findAll().size());
    }

    @Test
    void checkStatusUpdateToReady() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event eventCgm = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        Event eventCrac = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
        minioHandler.updateTasks(eventCrac);
        assertEquals(READY, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void checkStatusUpdateBackToCreated() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T21:00Z");
        Event eventCgm = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        Event eventCrac = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCrac);
        minioHandler.removeProcessFile(eventCrac);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void testCreationEventsForTwoFilesWithDifferentTypesAndSameTs() {
        Event eventCgm = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        Event eventCrac = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCrac);

        final List<ProcessEvent> processEvents = processEventRepository.findAll();
        assertEquals(2, processEvents.size());
        ProcessEvent event = processEvents.get(0);
        assertEquals("INFO", event.getLevel());
        assertEquals("A new version of CGM is available : 'cgm-test'", event.getMessage());
        event = processEvents.get(1);
        assertEquals("INFO", event.getLevel());
        assertEquals("A new version of CRAC is available : 'crac-test'", event.getMessage());
    }

    @Test
    void testUpdateEventsForTwoFilesWithSameTypeAndSameTs() {
        Event eventCgm = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        Event eventCgmNew = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CGM", "CSE/D2CC/CGMs/cgm-new-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");

        minioHandler.updateTasks(eventCgm);
        minioHandler.updateTasks(eventCgmNew);

        final List<ProcessEvent> processEvents = processEventRepository.findAll();
        assertEquals(2, processEvents.size());
        ProcessEvent event1 = processEvents.get(1);
        ProcessEvent event0 = processEvents.get(0);
        assertTrue(event0.getTimestamp().isBefore(event1.getTimestamp()));
        assertEquals("INFO", event1.getLevel());
        assertEquals("INFO", event0.getLevel());
        assertEquals("A new version of CGM is available : 'cgm-test'", event0.getMessage());
        assertEquals("A new version of CGM is available : 'cgm-new-test'", event1.getMessage());
    }

    @Test
    void testDeletionEventsOfTaskWithTwoDifferentFileTypes() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z").withOffsetSameInstant(ZoneOffset.UTC);
        Task task = new Task(taskTimestamp);
        final ProcessFile processFileCgm = new ProcessFile(
                "CSE/D2CC/CGMs/cgm-test",
                MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-01T21:00Z"),
                OffsetDateTime.parse("2021-10-01T22:00Z"),
                OffsetDateTime.now());
        task.addProcessFile(processFileCgm);
        final ProcessFile processFileCrac = new ProcessFile(
                "CSE/D2CC/CRACs/crac-test",
                MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "CRAC",
                "documentIdCrac",
                OffsetDateTime.parse("2021-10-01T21:00Z"),
                OffsetDateTime.parse("2021-10-01T22:00Z"),
                OffsetDateTime.now());
        task.addProcessFile(processFileCrac);

        taskRepository.save(task);

        Event eventCracDeletion = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        minioHandler.removeProcessFile(eventCracDeletion);

        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).orElseThrow();
        final List<ProcessEvent> processEvents = processEventRepository.findAll();
        assertEquals(1, processEvents.size());
        ProcessEvent event = processEvents.get(0);
        assertEquals("The CRAC : 'crac-test' is deleted", event.getMessage());
        assertEquals(1, updatedTask.getProcessFiles().size());
    }

    @Test
    void testDeletionEventsWithTaskDeletion() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        final ProcessFile processFileCrac = new ProcessFile(
                "CSE/D2CC/CRACs/crac-test",
                MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
                "CRAC",
                "documentIdCrac",
                OffsetDateTime.parse("2021-10-01T21:00Z"),
                OffsetDateTime.parse("2021-10-01T22:00Z"),
                OffsetDateTime.now());
        task.addProcessFile(processFileCrac);
        taskRepository.save(task);

        Event eventCracDeletion = TaskManagerTestUtil.createEvent("CSE_D2CC", INPUT_FILE_GROUP_VALUE, "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T21:00Z/2021-09-30T22:00Z");
        minioHandler.removeProcessFile(eventCracDeletion);

        assertEquals(NOT_CREATED, taskRepository.findByTimestamp(taskTimestamp).orElseThrow().getStatus());
    }

    @Test
    void testGetProcessFileMiniosNoMatchingTimestamps() {
        ProcessFile processFile1 = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile2 = new ProcessFile(
                "cgm-name2",
                "input",
                "CGM",
                "documentIdCgm2",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:30Z"));
        List<ProcessFileMinio> fileMinioList = new ArrayList<>();
        ProcessFileMinio file1 = new ProcessFileMinio(processFile1, null);
        ProcessFileMinio file2 = new ProcessFileMinio(processFile2, null);
        OffsetDateTime searchTimestamp = OffsetDateTime.parse("2021-10-13T10:18Z");

        fileMinioList.add(file1);
        fileMinioList.add(file2);

        ReflectionTestUtils.setField(minioHandler, "waitingFilesList", fileMinioList);
        List<ProcessFileMinio> result = minioHandler.getWaitingProcessFilesForTimestamp(searchTimestamp);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetProcessFileMiniosSomeMatchingTimestamps() {
        ProcessFile processFile1 = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile2 = new ProcessFile(
                "cgm-name2",
                "input",
                "CGM",
                "documentIdCgm2",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:30Z"));
        ProcessFile processFile3 = new ProcessFile(
                "cgm-name2",
                "input",
                "CGM",
                "documentIdCgm2",
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-13T00:00Z"),
                OffsetDateTime.parse("2021-10-13T10:30Z"));
        ProcessFileMinio file1 = new ProcessFileMinio(processFile1, null);
        ProcessFileMinio file2 = new ProcessFileMinio(processFile2, null);
        ProcessFileMinio file3 = new ProcessFileMinio(processFile3, null);

        List<ProcessFileMinio> fileMinioList = Arrays.asList(file1, file2, file3);

        ReflectionTestUtils.setField(minioHandler, "waitingFilesList", fileMinioList);
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:00Z");
        List<ProcessFileMinio> result = minioHandler.getWaitingProcessFilesForTimestamp(timestamp);

        assertEquals(2, result.size());
        assertTrue(result.contains(file1));
        assertTrue(result.contains(file2));
    }

    @Test
    void emptyWaitingListTest() {

        ProcessFile processFile1 = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile2 = new ProcessFile(
                "cgm-name2",
                "input",
                "CRAC",
                "documentIdCrac",
                OffsetDateTime.parse("2021-10-13T00:00Z"),
                OffsetDateTime.parse("2021-10-14T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:30Z"));
        List<ProcessFileMinio> fileMinioList = new ArrayList<>();
        ProcessFileMinio file1 = new ProcessFileMinio(processFile1, FileEventType.WAITING);
        ProcessFileMinio file2 = new ProcessFileMinio(processFile2, FileEventType.WAITING);
        OffsetDateTime searchTimestamp = OffsetDateTime.parse("2021-10-13T10:18Z");

        fileMinioList.add(file1);
        fileMinioList.add(file2);

        ReflectionTestUtils.setField(minioHandler, "waitingFilesList", fileMinioList);

        List before = (List) ReflectionTestUtils.getField(minioHandler, "waitingFilesList");
        assertEquals(2, before.size());
        minioHandler.emptyWaitingList(searchTimestamp);

        List after = (List) ReflectionTestUtils.getField(minioHandler, "waitingFilesList");
        assertEquals(1, after.size());
        //file 2 is removed
        assertEquals(file1, after.get(0));
    }

    @Test
    void emptyWaitingListTestEmpty() {

        List<ProcessFileMinio> fileMinioList = new ArrayList<>();
        OffsetDateTime searchTimestamp = OffsetDateTime.parse("2021-10-13T10:18Z");

        ReflectionTestUtils.setField(minioHandler, "waitingFilesList", fileMinioList);

        List before = (List) ReflectionTestUtils.getField(minioHandler, "waitingFilesList");
        assertTrue(before.isEmpty());
        minioHandler.emptyWaitingList(searchTimestamp);

        List after = (List) ReflectionTestUtils.getField(minioHandler, "waitingFilesList");
        assertTrue(after.isEmpty());
    }

    @Test
    void getProcessFileMinioFromDatabaseWithTypeInputTest() {
        OffsetDateTime startTime = OffsetDateTime.parse("2024-04-22T12:30Z");
        OffsetDateTime endTime = startTime.plusHours(1);
        String objectKey = "path/to/crac.xml";
        String fileType = "CRAC";
        String fileGroup = "input";
        String documentId = "documentIdCrac";
        OffsetDateTime lastModificationDate = OffsetDateTime.parse("2024-04-26T16:50Z");
        ProcessFile processFile = new ProcessFile(objectKey, fileGroup, fileType, documentId, startTime, endTime, lastModificationDate);
        processFileRepository.save(processFile);

        ProcessFileMinio processFileMinio = minioHandler.getProcessFileMinio(startTime, endTime, objectKey, fileType, fileGroup, documentId);

        assertNotNull(processFileMinio);
        assertEquals(objectKey, processFileMinio.getProcessFile().getFileObjectKey());
        assertNotEquals(lastModificationDate, processFileMinio.getProcessFile().getLastModificationDate());
        assertEquals(FileEventType.UPDATED, processFileMinio.getFileEventType());
    }

    @Test
    void getProcessFileMinioFromDatabaseWithTypeOutputTest() {
        OffsetDateTime startTime = OffsetDateTime.parse("2024-04-22T12:30Z");
        OffsetDateTime endTime = startTime.plusHours(1);
        String objectKey = "path/to/TTC.xml";
        String fileType = "TTC";
        String fileGroup = "output";
        OffsetDateTime lastModificationDate = OffsetDateTime.parse("2024-04-26T16:50Z");
        ProcessFile processFile = new ProcessFile(objectKey, fileGroup, fileType, null, startTime, endTime, lastModificationDate);
        processFileRepository.save(processFile);

        ProcessFileMinio processFileMinio = minioHandler.getProcessFileMinio(startTime, endTime, objectKey, fileType, fileGroup, null);

        assertNotNull(processFileMinio);
        assertEquals(objectKey, processFileMinio.getProcessFile().getFileObjectKey());
        assertNotEquals(lastModificationDate, processFileMinio.getProcessFile().getLastModificationDate());
        assertEquals(FileEventType.UPDATED, processFileMinio.getFileEventType());
    }

    @Test
    void getProcessFileMinioNotInDatabaseWithTypeInputTest() {
        OffsetDateTime startTime = OffsetDateTime.parse("2024-04-22T12:30Z");
        OffsetDateTime endTime = startTime.plusHours(1);
        String objectKey = "path/to/crac.xml";
        String fileType = "CRAC";
        String fileGroup = "input";
        String documentId = "documentIdCrac";

        ProcessFileMinio processFileMinio = minioHandler.getProcessFileMinio(startTime, endTime, objectKey, fileType, fileGroup, documentId);

        assertNotNull(processFileMinio);
        assertEquals(objectKey, processFileMinio.getProcessFile().getFileObjectKey());
        assertEquals(startTime, processFileMinio.getProcessFile().getStartingAvailabilityDate());
        assertEquals(endTime, processFileMinio.getProcessFile().getEndingAvailabilityDate());
        assertEquals(fileType, processFileMinio.getProcessFile().getFileType());
        assertEquals(fileGroup, processFileMinio.getProcessFile().getFileGroup());
        assertEquals(FileEventType.AVAILABLE, processFileMinio.getFileEventType());
    }

    @Test
    void getProcessFileMinioNotInDatabaseWithTypeOuputTest() {
        OffsetDateTime startTime = OffsetDateTime.parse("2024-04-22T12:30Z");
        OffsetDateTime endTime = startTime.plusHours(1);
        String objectKey = "path/to/TTC.xml";
        String fileType = "TTC";
        String fileGroup = "output";

        ProcessFileMinio processFileMinio = minioHandler.getProcessFileMinio(startTime, endTime, objectKey, fileType, fileGroup, null);

        assertNotNull(processFileMinio);
        assertEquals(objectKey, processFileMinio.getProcessFile().getFileObjectKey());
        assertEquals(startTime, processFileMinio.getProcessFile().getStartingAvailabilityDate());
        assertEquals(endTime, processFileMinio.getProcessFile().getEndingAvailabilityDate());
        assertEquals(fileType, processFileMinio.getProcessFile().getFileType());
        assertEquals(fileGroup, processFileMinio.getProcessFile().getFileGroup());
        assertEquals(FileEventType.AVAILABLE, processFileMinio.getFileEventType());
    }
}
