/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
class TaskTest {

    private Task task;

    @BeforeEach
    public void setUp() {
        task = new Task(LocalDateTime.now(), new ArrayList<>());
    }

    @Test
    void setId() {
        UUID newId = UUID.randomUUID();
        task.setId(newId);
        assertEquals(newId, task.getId());
    }

    @Test
    void setTimestamp() {
        LocalDateTime newDateTime = LocalDateTime.of(1, 1, 1, 1, 1, 1);
        task.setTimestamp(newDateTime);
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
        Mockito.when(processFileMock.getFileType()).thenReturn(fileType);
        List<ProcessFile> processFiles = new ArrayList<>();
        processFiles.add(processFileMock);
        task.setProcessFiles(processFiles);
        assertEquals(processFileMock, task.getProcessFiles().get(0));
        assertEquals(processFileMock, task.getProcessFile(fileType));
    }

    @Test
    void getProcessEventsTest() {
        assertTrue(task.getProcessEvents().isEmpty());
        ProcessEvent processEventMock = Mockito.mock(ProcessEvent.class);
        Mockito.when(processEventMock.getLevel()).thenReturn("WARN");
        List<ProcessEvent> ProcessEvents = new ArrayList<>();
        ProcessEvents.add(processEventMock);
        task.setProcessEvents(ProcessEvents);
        assertEquals(processEventMock, task.getProcessEvents().get(0));
        assertEquals("WARN", task.getProcessEvents().get(0).getLevel());
    }

    @Test
    void testConstructorFromEntity() {
        LocalDateTime timestamp = LocalDateTime.parse("2021-10-11T10:18");
        Task task = new Task(timestamp, List.of("CGM", "CRAC"));
        TaskDto taskDto = Task.createDtoFromEntity(task);
        assertEquals(timestamp, taskDto.getTimestamp());
        assertEquals(TaskStatus.CREATED, taskDto.getStatus());
        assertEquals(2, taskDto.getProcessFiles().size());
    }
}
