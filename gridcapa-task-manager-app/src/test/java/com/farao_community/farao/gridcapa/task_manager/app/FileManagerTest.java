/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@SpringBootTest
class FileManagerTest {

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private MinioAdapter minioAdapter;

    @MockitoBean
    private Logger businessLogger;

    @Autowired
    private FileManager fileManager;

    @Test
    void checkBytesForFileGroupGeneratedProperly() throws Exception {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Task task = new Task(taskTimestamp);
        when(taskRepository.findByTimestampAndFetchProcessEvents(taskTimestamp)).thenReturn(Optional.of(task));
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
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
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
    void areLogsExportableTest() {
        assertTrue(fileManager.areLogsExportable(MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE));
        assertFalse(fileManager.areLogsExportable(MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE));
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
        when(taskRepository.findByTimestampAndFetchProcessEvents(taskTimestamp)).thenReturn(Optional.of(task));
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
        when(taskRepository.findByTimestampAndFetchProcessEvents(timestamp)).thenReturn(Optional.of(task));
        try {
            assertNotNull(fileManager.getRaoRunnerAppLogs(timestamp));
        } catch (IOException e) {
            fail("GetRaoRunnerAppLogs should not throw exception");
        }
    }

    @Test
    void checkGetRaoRunnerAppLogsThrowsExceptionWhenNoTaskFound() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        assertThrows(TaskNotFoundException.class, () -> fileManager.getRaoRunnerAppLogs(taskTimestamp));
    }

    @Test
    void checkUploadFileToMinioInErrorIOException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        Mockito.doThrow(new IOException()).when(file).getInputStream();
        OffsetDateTime timestamp = OffsetDateTime.now();
        assertThrows(TaskManagerException.class, () -> fileManager.uploadFileToMinio(timestamp, file, "CGM", "TEST"));
    }

    @Test
    void testUploadFileToMinio() throws IOException {
        // Given
        final OffsetDateTime timestamp = OffsetDateTime.parse("2024-09-16T14:30Z");
        final String fileType = "text/plain";
        final String fileName = "testfile.txt";
        final String processTag = "CSE_D2CC";
        final String expectedPath = "cse/d2cc/MANUAL_UPLOAD/2024-09-16_1430/testfile.txt";

        final MultipartFile file = mock(MultipartFile.class);
        final InputStream inputStream = new ByteArrayInputStream("file content".getBytes());

        when(file.getInputStream()).thenReturn(inputStream);

        // When
        fileManager.uploadFileToMinio(timestamp, file, fileType, fileName);

        // Then
        verify(minioAdapter).uploadInputForTimestamp(expectedPath, inputStream, processTag, fileType, timestamp);
    }
}
