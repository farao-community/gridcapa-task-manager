/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.TaskDtoBuilder;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskTest {

    @Autowired
    private TaskDtoBuilder taskDtoBuilder;

    private Task task;

    @BeforeEach
    public void setUp() {
        task = new Task(OffsetDateTime.parse("2021-01-01T00:00Z"));
    }

    @Test
    void setId() {
        UUID newId = UUID.randomUUID();
        task.setId(newId);
        assertEquals(newId, task.getId());
    }

    @Test
    void setTimestamp() {
        OffsetDateTime newDateTime = OffsetDateTime.parse("2021-01-01T00:00Z");
        assertEquals(newDateTime, task.getTimestamp());
    }

    @Test
    void setStatus() {
        assert task.getStatus().equals(TaskStatus.CREATED);
        task.setStatus(TaskStatus.READY);
        assertEquals(TaskStatus.READY, task.getStatus());
    }

    @Test
    void getProcessFiles() {
        assertTrue(task.getProcessFiles().isEmpty());
        ProcessFile processFileMock = Mockito.mock(ProcessFile.class);
        String fileType = "testFileType";
        String fileGroup = "input";
        Mockito.when(processFileMock.getFileType()).thenReturn(fileType);
        Mockito.when(processFileMock.getFileGroup()).thenReturn(fileGroup);
        task.addProcessFile(processFileMock);
        assertEquals(processFileMock, task.getProcessFiles().iterator().next());
    }

    @Test
    void getInput() {
        assertTrue(task.getProcessFiles().isEmpty());
        ProcessFile processFileMock = Mockito.mock(ProcessFile.class);
        String fileType = "testFileType";
        String fileGroup = MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE;
        Mockito.when(processFileMock.getFileType()).thenReturn(fileType);
        Mockito.when(processFileMock.getFileGroup()).thenReturn(fileGroup);
        Mockito.when(processFileMock.getStartingAvailabilityDate()).thenReturn(task.getTimestamp());
        Mockito.when(processFileMock.getEndingAvailabilityDate()).thenReturn(task.getTimestamp().plusHours(1));
        task.addProcessFile(processFileMock);
        assertEquals(processFileMock, task.getInput(fileType).get());
    }

    @Test
    void getOutput() {
        assertTrue(task.getProcessFiles().isEmpty());
        ProcessFile processFileMock = Mockito.mock(ProcessFile.class);
        String fileType = "testFileType";
        String fileGroup = MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE;
        Mockito.when(processFileMock.getFileType()).thenReturn(fileType);
        Mockito.when(processFileMock.getFileGroup()).thenReturn(fileGroup);
        task.addProcessFile(processFileMock);
        assertEquals(processFileMock, task.getOutput(fileType).get());
    }

    @Test
    void getProcessEventsTest() {
        assertTrue(task.getProcessEvents().isEmpty());
        task.addProcessEvent(OffsetDateTime.now(), "WARN", "test");
        assertEquals("WARN", task.getProcessEvents().iterator().next().getLevel());
    }

    @Test
    void testConstructorFromEntity() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:18Z");
        Task task = new Task(timestamp);
        TaskDto taskDto = taskDtoBuilder.createDtoFromEntity(task);
        assertEquals(timestamp, taskDto.getTimestamp());
        assertEquals(TaskStatus.CREATED, taskDto.getStatus());
        assertEquals(2, taskDto.getInputs().size());
    }

    @Test
    void testEquals() {

        // Créer deux objets Task avec des horodatages identiques
        OffsetDateTime timestamp1 = OffsetDateTime.of(2023, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        Task task1 = new Task(timestamp1);

        OffsetDateTime timestamp2 = OffsetDateTime.of(2023, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        Task task2 = new Task(timestamp2);

        // Vérifier que les deux objets sont égaux
        assertEquals(task1, task2);
        assertEquals(task2, task1);

        // Modifier l'horodatage de task2
        OffsetDateTime timestamp3 = OffsetDateTime.of(2023, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        Task task3 = new Task(timestamp3);

        // Vérifier que les objets ne sont plus égaux
        assertNotEquals(task1, task3);
        assertNotEquals(task3, task1);

        // Créer un autre objet Task avec un horodatage différent
        OffsetDateTime timestamp4 = OffsetDateTime.of(2023, 6, 2, 10, 0, 0, 0, ZoneOffset.UTC);
        Task task4 = new Task(timestamp4);

        // Vérifier que les objets ne sont pas égaux
        assertNotEquals(task1, task4);
        assertNotEquals(task4, task1);

        // Vérifier qu'un objet Task n'est pas égal à null
        assertNotEquals(null, task1);
    }
}

