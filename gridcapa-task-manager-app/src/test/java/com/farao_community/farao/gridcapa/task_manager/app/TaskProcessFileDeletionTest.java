/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.service.MinioHandler;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import io.minio.messages.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@SpringBootTest
class TaskProcessFileDeletionTest {

    @MockBean
    private TaskUpdateNotifier taskUpdateNotifier; // Useful to avoid AMQP connection that would fail

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProcessFileRepository processFileRepository;

    @Autowired
    private MinioHandler minioHandler;

    private final OffsetDateTime offsetDateTime0 = OffsetDateTime.of(2020, 1, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime offsetDateTime1 = OffsetDateTime.of(2020, 1, 1, 1, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime offsetDateTime2 = OffsetDateTime.of(2020, 1, 1, 2, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime offsetDateTime3 = OffsetDateTime.of(2020, 1, 1, 3, 30, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUpTasks() {
        Task task1 = new Task(offsetDateTime0);
        Task task2 = new Task(offsetDateTime1);
        Task task3 = new Task(offsetDateTime2);
        Task task4 = new Task(offsetDateTime3);

        ProcessFile cgmFile1 = new ProcessFile(
            "/CGM",
            MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "CGM",
            offsetDateTime0,
            offsetDateTime0.plusHours(1),
            OffsetDateTime.now());
        processFileRepository.save(cgmFile1);

        ProcessFile cgmFile2 = new ProcessFile(
            "/CGM2",
            MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "CGM",
            offsetDateTime1,
            offsetDateTime1.plusHours(1),
            OffsetDateTime.now());
        processFileRepository.save(cgmFile2);

        ProcessFile cgmFile3 = new ProcessFile(
            "/CGM3",
            MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "CGM",
            offsetDateTime2,
            offsetDateTime2.plusHours(1),
            OffsetDateTime.now());
        processFileRepository.save(cgmFile3);

        ProcessFile refprogFile = new ProcessFile(
            "/REFPROG",
            MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "REFPROG",
            offsetDateTime0,
            offsetDateTime0.plusHours(4),
            OffsetDateTime.now());
        processFileRepository.save(refprogFile);

        task1.addProcessFile(cgmFile1);
        task1.addProcessFile(refprogFile);
        taskRepository.save(task1);
        task2.addProcessFile(cgmFile2);
        task2.addProcessFile(refprogFile);
        taskRepository.save(task2);
        task3.addProcessFile(cgmFile3);
        taskRepository.save(task3);
        task4.addProcessFile(refprogFile);
        taskRepository.save(task4);
    }

    @AfterEach
    void cleanTasks() {
        taskRepository.deleteAll();
        processFileRepository.deleteAll();
    }

    @Test
    void checkThatNotUsedFileDoesNotTriggerAnyDeletion() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/CGMOther");

        minioHandler.removeProcessFile(event);

        Task task1 = taskRepository.findByTimestamp(offsetDateTime0).orElseThrow();
        assertTrue(task1.getInput("CGM").isPresent());
        Task task2 = taskRepository.findByTimestamp(offsetDateTime1).orElseThrow();
        assertTrue(task2.getInput("CGM").isPresent());
    }

    @Test
    void checkThatUsedFileTriggerFileDeletionWithoutRemovingTask() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/CGM2");

        Task task2 = taskRepository.findByTimestamp(offsetDateTime1).orElseThrow();
        assertTrue(task2.getInput("CGM").isPresent());

        minioHandler.removeProcessFile(event);

        task2 = taskRepository.findByTimestamp(offsetDateTime1).orElseThrow();
        assertTrue(task2.getInput("CGM").isEmpty());
    }

    @Test
    void checkThatUsedFileTriggerFileDeletionRemovingTask() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/CGM3");
        assertTrue(taskRepository.findByTimestamp(offsetDateTime2).isPresent());

        minioHandler.removeProcessFile(event);

        assertEquals(TaskStatus.NOT_CREATED, taskRepository.findByTimestamp(offsetDateTime2).orElseThrow().getStatus());
    }

    @Test
    void checkThatUsedMultipleTimesFileTriggerFileDeletionWithAndWithoutRemovingTask() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/REFPROG");

        assertTrue(taskRepository.findByTimestamp(offsetDateTime0).orElseThrow().getInput("REFPROG").isPresent());
        assertTrue(taskRepository.findByTimestamp(offsetDateTime1).orElseThrow().getInput("REFPROG").isPresent());
        assertTrue(taskRepository.findByTimestamp(offsetDateTime3).orElseThrow().getInput("REFPROG").isPresent());

        minioHandler.removeProcessFile(event);

        assertTrue(taskRepository.findByTimestamp(offsetDateTime0).orElseThrow().getInput("REFPROG").isEmpty());
        assertTrue(taskRepository.findByTimestamp(offsetDateTime1).orElseThrow().getInput("REFPROG").isEmpty());

        assertEquals(TaskStatus.NOT_CREATED, taskRepository.findByTimestamp(offsetDateTime3).orElseThrow().getStatus());
    }

}
