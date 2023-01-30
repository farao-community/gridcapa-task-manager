/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskManagerControllerTest {

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private TaskManager taskManager;

    @MockBean
    private FileManager fileManager;

    @Autowired
    private TaskManagerController taskManagerController;

    @Autowired
    private MinioAdapter minioAdapter;

    @Test
    void testGetTaskOk() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Task task = new Task(taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(taskTimestamp)).thenReturn(Optional.of(task));
        ResponseEntity<TaskDto> taskResponse = taskManagerController.getTaskFromTimestamp(taskTimestamp.toString());

        assertEquals(HttpStatus.OK, taskResponse.getStatusCode());
    }

    @Test
    void testGetListTasksOk() {
        LocalDate businessDate = LocalDate.parse("2021-01-30");
        ResponseEntity<List<TaskDto>> taskResponse = taskManagerController.getListTasksFromBusinessDate(businessDate.toString());
        assertEquals(HttpStatus.OK, taskResponse.getStatusCode());
        assertEquals(24, taskResponse.getBody().size());
    }

    @Test
    void testUpdateWithInvalidTaskStatus() {
        ResponseEntity<TaskDto> taskResponse = taskManagerController.updateStatus("2021-09-30T23:00Z", "WRONG_STATUS");
        assertEquals(HttpStatus.BAD_REQUEST, taskResponse.getStatusCode());
    }

    @Test
    void testUpdateWithNotFoundTask() {
        String timestamp = "2021-09-30T23:00Z";
        Mockito.when(taskRepository.findByTimestamp(OffsetDateTime.parse(timestamp))).thenReturn(Optional.empty());
        ResponseEntity<TaskDto> taskResponse = taskManagerController.updateStatus(timestamp, "RUNNING");
        assertEquals(HttpStatus.NOT_FOUND, taskResponse.getStatusCode());
    }

    @Test
    void testUpdateOk() {
        String timestamp = "2021-09-30T23:00Z";
        OffsetDateTime taskTimestamp = OffsetDateTime.parse(timestamp);
        Task task = new Task(taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(OffsetDateTime.parse(timestamp))).thenReturn(Optional.of(task));
        Mockito.doAnswer(answer -> {
            task.setStatus(TaskStatus.RUNNING);
            return Optional.of(task);
        }).when(taskManager).handleTaskStatusUpdate(taskTimestamp, TaskStatus.RUNNING);
        ResponseEntity<TaskDto> taskResponse = taskManagerController.updateStatus(timestamp, "RUNNING");
        assertEquals(HttpStatus.OK, taskResponse.getStatusCode());
        assertEquals(TaskStatus.RUNNING, taskResponse.getBody().getStatus());
    }

    @Test
    void testZipInputsExportOk() throws Exception {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Mockito.when(fileManager.getZippedGroup(Mockito.any(), Mockito.eq(MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE))).thenReturn(new ByteArrayOutputStream());
        Mockito.when(fileManager.getZipName(Mockito.any(), Mockito.eq(MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE))).thenReturn("2021-10-01_0130_input.zip");
        ResponseEntity<byte[]> inputsBytesResponse = taskManagerController.getZippedInputs(taskTimestamp.toString());
        assertEquals(HttpStatus.OK, inputsBytesResponse.getStatusCode());
        assertEquals("attachment;filename=\"2021-10-01_0130_input.zip\"", inputsBytesResponse.getHeaders().get("Content-Disposition").get(0));
    }

    @Test
    void testZipOutputsExportOk() throws Exception {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Mockito.when(fileManager.getZippedGroup(Mockito.any(), Mockito.eq(MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE))).thenReturn(new ByteArrayOutputStream());
        Mockito.when(fileManager.getZipName(Mockito.any(), Mockito.eq(MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE))).thenReturn("2021-10-01_0130_output.zip");
        ResponseEntity<byte[]> outputsBytesResponse = taskManagerController.getZippedOutputs(taskTimestamp.toString());
        assertEquals(HttpStatus.OK, outputsBytesResponse.getStatusCode());
        assertEquals("attachment;filename=\"2021-10-01_0130_output.zip\"", outputsBytesResponse.getHeaders().get("Content-Disposition").get(0));
    }

    @Test
    void testGetUnknownFile() throws Exception {
        String timestamp = "2021-09-30T23:00Z";
        OffsetDateTime taskTimestamp = OffsetDateTime.parse(timestamp);
        String fileType = "CrACk"; //bad file type
        Task task = new Task(taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(taskTimestamp)).thenReturn(Optional.of(task));
        ResponseEntity<byte[]> taskResponse = taskManagerController.getFile(fileType, timestamp);
        assertEquals(HttpStatus.NOT_FOUND, taskResponse.getStatusCode());
    }

    @Test
    void testGetFile() throws Exception {
        String timestamp = "2021-09-01T23:30Z";
        OffsetDateTime taskTimestamp = OffsetDateTime.parse(timestamp);
        String fileType = "CRAC";
        String fakeUrl = "http://fakeUrl";
        Task task = new Task(taskTimestamp);
        task.addProcessFile("FAKE", "input", fileType,  taskTimestamp, taskTimestamp, fakeUrl, taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(taskTimestamp)).thenReturn(Optional.of(task));
        Mockito.when(fileManager.openUrlStream(anyString())).thenReturn(InputStream.nullInputStream());
        Mockito.when(fileManager.generatePresignedUrl(anyString())).thenReturn("MinioUrl");
        ResponseEntity<byte[]> taskResponse = taskManagerController.getFile(fileType, timestamp);
        assertEquals(HttpStatus.OK, taskResponse.getStatusCode());
    }
}
