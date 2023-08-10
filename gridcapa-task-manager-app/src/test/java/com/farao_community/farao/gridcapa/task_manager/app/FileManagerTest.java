/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@SpringBootTest
class FileManagerTest {

    @MockBean
    private TaskRepository taskRepository;

    @Autowired
    private FileManager fileManager;

    @Test
    void checkBytesForFileGroupGeneratedProperly() throws Exception {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Task task = new Task(taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(taskTimestamp)).thenReturn(Optional.of(task));
        assertNotNull(fileManager.getZippedGroup(taskTimestamp, MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE));
    }

    @Test
    void checkThrowsExceptionWhenNoTaskAtGivenDate() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        assertThrows(TaskNotFoundException.class, () -> fileManager.getZippedGroup(taskTimestamp, MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE));
    }

    @Test
    void checkBytesForFileGroupGeneratedProperlyFromId() throws Exception {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Task task = new Task(taskTimestamp);
        Mockito.when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        assertNotNull(fileManager.getZippedGroupById(task.getId().toString(), MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE));
    }

    @Test
    void checkThrowsExceptionWhenNoTaskAtGivenDateById() {
        String uuid = UUID.randomUUID().toString();
        assertThrows(TaskNotFoundException.class, () -> fileManager.getZippedGroupById(uuid, MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE));
    }

    @Test
    void getZipNameTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        String actualZipName = fileManager.getZipName(taskTimestamp, MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE);
        String expectedZipName = "2021-10-01_0130_output.zip";
        assertEquals(expectedZipName, actualZipName);
    }

    @Test
    void isExportLogsEnabledAndFileGroupIsGridcapaOutputTest() {
        assertTrue(fileManager.isExportLogsEnabledAndFileGroupIsGridcapaOutput(MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE));
        assertFalse(fileManager.isExportLogsEnabledAndFileGroupIsGridcapaOutput(MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE));
    }

    @Test
    void checkGetLogsThrowsExceptionWhenNoTaskAtGivenDate() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        assertThrows(TaskNotFoundException.class, () -> fileManager.getLogs(taskTimestamp));
    }

    @Test
    void checkGetLogs() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Task task = new Task(taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(taskTimestamp)).thenReturn(Optional.of(task));
        try {
            assertNotNull(fileManager.getLogs(taskTimestamp));
        } catch (IOException e) {
            fail("GetLogs should not throw exception");
        }
    }

    @Test
    void testGetRaoRunnerAppLogs() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Task task = new Task(timestamp);
        Mockito.when(taskRepository.findByTimestamp(timestamp)).thenReturn(Optional.of(task));
        try {
            assertNotNull(fileManager.getRaoRunnerAppLogs(timestamp));
        } catch (IOException e) {
            fail("GetRaoRunnerAppLogs should not throw exception");
        }
    }

    @Test
    void checkGetLogsThrowsExceptionWhenNoTaskFound() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        assertThrows(TaskNotFoundException.class, () -> fileManager.getRaoRunnerAppLogs(taskTimestamp));
    }

}
