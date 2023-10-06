/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.service.StatusHandler;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskManagerControllerTest {

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private StatusHandler statusHandler;

    @MockBean
    private FileManager fileManager;

    @Autowired
    private TaskManagerController taskManagerController;

    @Autowired
    private TaskManagerConfigurationProperties taskManagerConfigurationProperties;

    @Autowired
    private MinioAdapter minioAdapter;

    @Autowired
    private TaskManagerConfigurationProperties properties;

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
        }).when(statusHandler).handleTaskStatusUpdate(taskTimestamp, TaskStatus.RUNNING);
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
        task.addProcessFile("FAKE", "input", fileType, taskTimestamp, taskTimestamp, taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(taskTimestamp)).thenReturn(Optional.of(task));
        Mockito.when(fileManager.openUrlStream(anyString())).thenReturn(InputStream.nullInputStream());
        Mockito.when(fileManager.generatePresignedUrl(anyString())).thenReturn("MinioUrl");
        ResponseEntity<byte[]> taskResponse = taskManagerController.getFile(fileType, timestamp);
        assertEquals(HttpStatus.OK, taskResponse.getStatusCode());
    }

    @Test
    void testGetRaoLogFileWithLocalDateTimeSummerTime() throws Exception {
        String timestamp = "2021-07-01T23:30Z";
        OffsetDateTime taskTimestamp = OffsetDateTime.parse(timestamp);
        String fileNameLocalDateTime = taskTimestamp.atZoneSameInstant(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone())).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));
        String fileType = "LOGS";
        Task task = new Task(taskTimestamp);
        task.addProcessFile("FAKE", "input", fileType, taskTimestamp, taskTimestamp, taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(taskTimestamp)).thenReturn(Optional.of(task));
        Mockito.when(fileManager.openUrlStream(anyString())).thenReturn(InputStream.nullInputStream());
        Mockito.when(fileManager.generatePresignedUrl(anyString())).thenReturn("MinioUrl");
        Mockito.when(fileManager.getLogs(Mockito.any(OffsetDateTime.class))).thenReturn(new ByteArrayOutputStream(0));
        ResponseEntity<byte[]> taskResponse = taskManagerController.getFile(fileType, timestamp);
        assertEquals(HttpStatus.OK, taskResponse.getStatusCode());
        String expected = "[attachment;filename=\"rao_logs_" + fileNameLocalDateTime + ".zip\"]";
        assertEquals(expected, taskResponse.getHeaders().get("Content-Disposition").toString());
    }

    @Test
    void testGetRaoLogFileWithLocalDateTimeWinterTime() throws Exception {
        String timestamp = "2021-12-01T23:30Z";
        OffsetDateTime taskTimestamp = OffsetDateTime.parse(timestamp);
        String fileNameLocalDateTime = taskTimestamp.atZoneSameInstant(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone())).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));
        String fileType = "LOGS";
        Task task = new Task(taskTimestamp);
        task.addProcessFile("FAKE", "input", fileType, taskTimestamp, taskTimestamp, taskTimestamp);
        Mockito.when(taskRepository.findByTimestamp(taskTimestamp)).thenReturn(Optional.of(task));
        Mockito.when(fileManager.openUrlStream(anyString())).thenReturn(InputStream.nullInputStream());
        Mockito.when(fileManager.generatePresignedUrl(anyString())).thenReturn("MinioUrl");
        Mockito.when(fileManager.getLogs(Mockito.any(OffsetDateTime.class))).thenReturn(new ByteArrayOutputStream(0));
        ResponseEntity<byte[]> taskResponse = taskManagerController.getFile(fileType, timestamp);
        assertEquals(HttpStatus.OK, taskResponse.getStatusCode());
        String expected = "[attachment;filename=\"rao_logs_" + fileNameLocalDateTime + ".zip\"]";
        assertEquals(expected, taskResponse.getHeaders().get("Content-Disposition").toString());
    }

    @Test
    void testAreAllTasksFromBusinessDateOverShouldReturnFalse() {
        LocalDate businessDate = LocalDate.parse("2021-01-01");
        Task task = new Task();
        task.setStatus(TaskStatus.CREATED);
        Mockito.when(taskRepository.findAllByTimestampBetweenForBusinessDayView(Mockito.any(), Mockito.any())).thenReturn(Set.of(task));
        ResponseEntity<Boolean> response = taskManagerController.areAllTasksFromBusinessDateOver(businessDate.toString());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody());
    }

    @Test
    void testAreAllTasksFromBusinessDateOverShouldReturnTrue() {
        LocalDate businessDate = LocalDate.parse("2021-01-01");
        ZoneId zone = ZoneId.of(properties.getProcess().getTimezone());
        LocalDateTime businessDateStartTime = businessDate.atTime(0, 30);
        ZoneOffset zoneOffSet = zone.getRules().getOffset(businessDateStartTime);
        OffsetDateTime startTimestamp = businessDateStartTime.atOffset(zoneOffSet);
        Map<OffsetDateTime, TaskDto> taskMap = new HashMap<>();
        Set<Task> tasks = new HashSet<>();
        while (startTimestamp.getDayOfMonth() == businessDate.getDayOfMonth()) {
            Task task = new Task(startTimestamp);
            task.setStatus(TaskStatus.ERROR);
            tasks.add(task);
            startTimestamp = startTimestamp.plusHours(1).atZoneSameInstant(zone).toOffsetDateTime();
        }
        Mockito.when(taskRepository.findAllByTimestampBetweenForBusinessDayView(Mockito.any(), Mockito.any())).thenReturn(tasks);
        ResponseEntity<Boolean> response = taskManagerController.areAllTasksFromBusinessDateOver(businessDate.toString());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody());
    }

    @Test
    void testGetLogOk() throws Exception {
        String timestamp = "2021-09-02T22:30Z";
        Mockito.when(fileManager.getRaoRunnerAppLogs(OffsetDateTime.parse(timestamp))).thenReturn(new ByteArrayOutputStream(0));
        ResponseEntity<byte[]> taskResponse = taskManagerController.getLog(timestamp);
        assertEquals(HttpStatus.OK, taskResponse.getStatusCode());
    }
}
