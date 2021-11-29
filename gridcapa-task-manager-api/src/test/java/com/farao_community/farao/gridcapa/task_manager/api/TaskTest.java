/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        assert task.getId().equals(newId);
    }

    @Test
    void setTimestamp() {
        LocalDateTime newDateTime = LocalDateTime.of(1, 1, 1, 1, 1, 1);
        task.setTimestamp(newDateTime);
        assert task.getTimestamp().equals(newDateTime);
    }

    @Test
    void setStatus() {
        assert task.getStatus().equals(TaskStatus.CREATED);
        task.setStatus(TaskStatus.READY);
        assert task.getStatus().equals(TaskStatus.READY);
    }

    @Test
    void getProcessFiles() {
        assert task.getProcessFiles().isEmpty();
        ProcessFile processFileMock = Mockito.mock(ProcessFile.class);
        String fileType = "testFileType";
        Mockito.when(processFileMock.getFileType()).thenReturn(fileType);
        List<ProcessFile> processFiles = new ArrayList<>();
        processFiles.add(processFileMock);
        task.setProcessFiles(processFiles);
        assert task.getProcessFiles().get(0).equals(processFileMock);
        assert task.getProcessFile(fileType).equals(processFileMock);
    }
}
