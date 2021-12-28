/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
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
import java.util.Arrays;
import java.util.List;

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
    private TaskManager taskManager;

    private OffsetDateTime offsetDateTime0 = OffsetDateTime.of(2020, 1, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private OffsetDateTime offsetDateTime1 = OffsetDateTime.of(2020, 1, 1, 1, 30, 0, 0, ZoneOffset.UTC);
    private OffsetDateTime offsetDateTime2 = OffsetDateTime.of(2020, 1, 1, 2, 30, 0, 0, ZoneOffset.UTC);
    private OffsetDateTime offsetDateTime3 = OffsetDateTime.of(2020, 1, 1, 3, 30, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUpTasks() {
        List<String> fileTypes = Arrays.asList("CGM", "GLSK", "REFPROG");

        Task task1 = new Task(offsetDateTime0);
        Task task2 = new Task(offsetDateTime1);
        Task task3 = new Task(offsetDateTime2);
        Task task4 = new Task(offsetDateTime3);

        ProcessFile cgmFile1 = new ProcessFile("CGM");
        cgmFile1.setFileObjectKey("/CGM");
        cgmFile1.setFileUrl("http://CGM");
        cgmFile1.setFilename("CGM");
        cgmFile1.setLastModificationDate(OffsetDateTime.now());

        ProcessFile cgmFile2 = new ProcessFile("CGM");
        cgmFile2.setFileObjectKey("/CGM2");
        cgmFile2.setFileUrl("http://CGM2");
        cgmFile2.setFilename("CGM2");
        cgmFile2.setLastModificationDate(OffsetDateTime.now());

        ProcessFile cgmFile3 = new ProcessFile("CGM");
        cgmFile3.setFileObjectKey("/CGM3");
        cgmFile3.setFileUrl("http://CGM3");
        cgmFile3.setFilename("CGM3");
        cgmFile3.setLastModificationDate(OffsetDateTime.now());

        ProcessFile refprogFile = new ProcessFile("REFPROG");
        refprogFile.setFileObjectKey("/REFPROG");
        refprogFile.setFileUrl("http://REFPROG");
        refprogFile.setFilename("REFPROG");
        refprogFile.setLastModificationDate(OffsetDateTime.now());

        cgmFile1.addTask(task1);
        processFileRepository.save(cgmFile1);
        cgmFile2.addTask(task2);
        processFileRepository.save(cgmFile2);
        cgmFile3.addTask(task3);
        processFileRepository.save(cgmFile3);
        refprogFile.addTask(task1);
        refprogFile.addTask(task2);
        refprogFile.addTask(task4);
        processFileRepository.save(refprogFile);
    }

    @AfterEach
    void cleanTasks() {
        processFileRepository.deleteAll();
        taskRepository.deleteAll();
    }

    @Test
    void checkThatNotUsedFileDoesNotTriggerAnyDeletion() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/CGMOther");

        taskManager.removeProcessFile(event);

        Task task1 = taskRepository.findByTimestamp(offsetDateTime0).get();
        assertTrue(task1.getProcessFile("CGM").isPresent());
        Task task2 = taskRepository.findByTimestamp(offsetDateTime1).get();
        assertTrue(task2.getProcessFile("CGM").isPresent());
    }

    @Test
    void checkThatUsedFileTriggerFileDeletionWithoutRemovingTask() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/CGM2");

        Task task2 = taskRepository.findByTimestamp(offsetDateTime1).get();
        assertTrue(task2.getProcessFile("CGM").isPresent());

        taskManager.removeProcessFile(event);

        task2 = taskRepository.findByTimestamp(offsetDateTime1).get();
        assertTrue(task2.getProcessFile("CGM").isEmpty());
    }

    @Test
    void checkThatUsedFileTriggerFileDeletionRemovingTask() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/CGM3");
        assertTrue(taskRepository.findByTimestamp(offsetDateTime2).isPresent());

        taskManager.removeProcessFile(event);

        assertFalse(taskRepository.findByTimestamp(offsetDateTime2).isPresent());
    }

    @Test
    void checkThatUsedMultipleTimesFileTriggerFileDeletionWithAndWithoutRemovingTask() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/REFPROG");

        assertTrue(taskRepository.findByTimestamp(offsetDateTime0).get().getProcessFile("REFPROG").isPresent());
        assertTrue(taskRepository.findByTimestamp(offsetDateTime1).get().getProcessFile("REFPROG").isPresent());
        assertTrue(taskRepository.findByTimestamp(offsetDateTime3).get().getProcessFile("REFPROG").isPresent());

        taskManager.removeProcessFile(event);

        assertTrue(taskRepository.findByTimestamp(offsetDateTime0).get().getProcessFile("REFPROG").isEmpty());
        assertTrue(taskRepository.findByTimestamp(offsetDateTime1).get().getProcessFile("REFPROG").isEmpty());
        assertFalse(taskRepository.findByTimestamp(offsetDateTime3).isPresent());
    }

}
