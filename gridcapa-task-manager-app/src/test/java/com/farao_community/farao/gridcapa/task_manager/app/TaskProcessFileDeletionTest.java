/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;
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
    private TaskManager taskManager;

    private OffsetDateTime offsetDateTime0 = OffsetDateTime.of(2020, 1, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private OffsetDateTime offsetDateTime1 = OffsetDateTime.of(2020, 1, 1, 1, 30, 0, 0, ZoneOffset.UTC);
    private OffsetDateTime offsetDateTime2 = OffsetDateTime.of(2020, 1, 1, 2, 30, 0, 0, ZoneOffset.UTC);
    private OffsetDateTime offsetDateTime3 = OffsetDateTime.of(2020, 1, 1, 3, 30, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUpTasks() {
        List<String> fileTypes = Arrays.asList("CGM", "GLSK", "REFPROG");
        Task task1 = new Task(offsetDateTime0, fileTypes);
        ProcessFile cgmFile1 = task1.getProcessFile("CGM");
        cgmFile1.setFileObjectKey("/CGM");
        cgmFile1.setFileUrl("http://CGM");
        cgmFile1.setFilename("CGM");
        cgmFile1.setLastModificationDate(OffsetDateTime.now());
        cgmFile1.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        ProcessFile refprogFile1 = task1.getProcessFile("REFPROG");
        refprogFile1.setFileObjectKey("/REFPROG");
        refprogFile1.setFileUrl("http://REFPROG");
        refprogFile1.setFilename("REFPROG");
        refprogFile1.setLastModificationDate(OffsetDateTime.now());
        refprogFile1.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        taskRepository.save(task1);

        Task task2 = new Task(offsetDateTime1, fileTypes);
        ProcessFile cgmFile2 = task2.getProcessFile("CGM");
        cgmFile2.setFileObjectKey("/CGM2");
        cgmFile2.setFileUrl("http://CGM2");
        cgmFile2.setFilename("CGM2");
        cgmFile2.setLastModificationDate(OffsetDateTime.now());
        cgmFile2.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        ProcessFile refprogFile2 = task2.getProcessFile("REFPROG");
        refprogFile2.setFileObjectKey("/REFPROG");
        refprogFile2.setFileUrl("http://REFPROG");
        refprogFile2.setFilename("REFPROG");
        refprogFile2.setLastModificationDate(OffsetDateTime.now());
        refprogFile2.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        taskRepository.save(task2);

        Task task3 = new Task(offsetDateTime2, fileTypes);
        ProcessFile cgmFile3 = task3.getProcessFile("CGM");
        cgmFile3.setFileObjectKey("/CGM3");
        cgmFile3.setFileUrl("http://CGM3");
        cgmFile3.setFilename("CGM3");
        cgmFile3.setLastModificationDate(OffsetDateTime.now());
        cgmFile3.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        taskRepository.save(task3);

        Task task4 = new Task(offsetDateTime3, fileTypes);
        ProcessFile refprogFile4 = task4.getProcessFile("REFPROG");
        refprogFile4.setFileObjectKey("/REFPROG");
        refprogFile4.setFileUrl("http://REFPROG");
        refprogFile4.setFilename("REFPROG");
        refprogFile4.setLastModificationDate(OffsetDateTime.now());
        refprogFile4.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        taskRepository.save(task4);
    }

    @AfterEach
    void cleanTasks() {
        taskRepository.deleteAll();
    }

    @Test
    void checkThatNotUsedFileDoesNotTriggerAnyDeletion() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/CGMOther");

        taskManager.removeProcessFile(event);

        Task task1 = taskRepository.findByTimestamp(offsetDateTime0).get();
        assertEquals(ProcessFileStatus.VALIDATED, task1.getProcessFile("CGM").getProcessFileStatus());
        Task task2 = taskRepository.findByTimestamp(offsetDateTime1).get();
        assertEquals(ProcessFileStatus.VALIDATED, task2.getProcessFile("CGM").getProcessFileStatus());
    }

    @Test
    void checkThatUsedFileTriggerFileDeletionWithoutRemovingTask() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.objectName()).thenReturn("/CGM2");
        Task task2 = taskRepository.findByTimestamp(offsetDateTime1).get();
        assertEquals(ProcessFileStatus.VALIDATED, task2.getProcessFile("CGM").getProcessFileStatus());

        taskManager.removeProcessFile(event);

        task2 = taskRepository.findByTimestamp(offsetDateTime1).get();
        assertEquals(ProcessFileStatus.DELETED, task2.getProcessFile("CGM").getProcessFileStatus());
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

        assertEquals(ProcessFileStatus.VALIDATED, taskRepository.findByTimestamp(offsetDateTime0).get().getProcessFile("REFPROG").getProcessFileStatus());
        assertEquals(ProcessFileStatus.VALIDATED, taskRepository.findByTimestamp(offsetDateTime1).get().getProcessFile("REFPROG").getProcessFileStatus());
        assertEquals(ProcessFileStatus.VALIDATED, taskRepository.findByTimestamp(offsetDateTime3).get().getProcessFile("REFPROG").getProcessFileStatus());

        taskManager.removeProcessFile(event);

        assertEquals(ProcessFileStatus.DELETED, taskRepository.findByTimestamp(offsetDateTime0).get().getProcessFile("REFPROG").getProcessFileStatus());
        assertEquals(ProcessFileStatus.DELETED, taskRepository.findByTimestamp(offsetDateTime1).get().getProcessFile("REFPROG").getProcessFileStatus());
        assertFalse(taskRepository.findByTimestamp(offsetDateTime3).isPresent());
    }

}
