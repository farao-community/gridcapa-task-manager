/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskManagerControllerTest {

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private FileManager fileManager;

    @Autowired
    private TaskManagerController taskManagerController;

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
    void testZipInputsExportOk() throws Exception {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Mockito.when(fileManager.getZippedGroup(Mockito.any(), Mockito.eq(MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE))).thenReturn(new ByteArrayOutputStream());
        ResponseEntity<byte[]> inputsBytesResponse = taskManagerController.getZippedInputs(taskTimestamp.toString());
        assertEquals(HttpStatus.OK, inputsBytesResponse.getStatusCode());
        assertEquals("attachment;filename=\"input.zip\"", inputsBytesResponse.getHeaders().get("Content-Disposition").get(0));
    }

    @Test
    void testZipOutputsExportOk() throws Exception {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-09-30T23:00Z");
        Mockito.when(fileManager.getZippedGroup(Mockito.any(), Mockito.eq(MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE))).thenReturn(new ByteArrayOutputStream());
        ResponseEntity<byte[]> outputsBytesResponse = taskManagerController.getZippedOutputs(taskTimestamp.toString());
        assertEquals(HttpStatus.OK, outputsBytesResponse.getStatusCode());
        assertEquals("attachment;filename=\"output.zip\"", outputsBytesResponse.getHeaders().get("Content-Disposition").get(0));
    }
}
