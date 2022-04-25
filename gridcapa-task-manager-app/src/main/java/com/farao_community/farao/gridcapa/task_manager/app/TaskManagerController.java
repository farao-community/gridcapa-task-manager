/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Controller
@RequestMapping
public class TaskManagerController {

    private final TaskDtoBuilder builder;
    private final FileManager fileManager;

    public TaskManagerController(TaskDtoBuilder builder, FileManager fileManager) {
        this.builder = builder;
        this.fileManager = fileManager;
    }

    @GetMapping(value = "/tasks/{timestamp}")
    public ResponseEntity<TaskDto> getTaskFromTimestamp(@PathVariable String timestamp) {
        return ResponseEntity.ok().body(builder.getTaskDto(OffsetDateTime.parse(timestamp)));
    }

    @GetMapping(value = "/tasks/{timestamp}/inputs", produces = "application/octet-stream")
    public ResponseEntity<byte[]> getZippedInputs(@PathVariable String timestamp) {
        return getZippedGroup(OffsetDateTime.parse(timestamp), MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE);
    }

    @GetMapping(value = "/tasks/{timestamp}/outputs", produces = "application/octet-stream")
    public ResponseEntity<byte[]> getZippedOutputs(@PathVariable String timestamp) {
        return getZippedGroup(OffsetDateTime.parse(timestamp), MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE);
    }

    private ResponseEntity<byte[]> getZippedGroup(OffsetDateTime timestamp, String fileGroup) {
        try {
            ByteArrayOutputStream zip = fileManager.getZippedGroup(timestamp, fileGroup);
            String zipName = fileGroup + ".zip";
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment;filename=\"" + zipName + "\"")
                .body(zip.toByteArray());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        } catch (TaskNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // TODO: To be removed, task-manager should expose timestamp instead
    @GetMapping(value = "/tasks/{id}/outputs-by-id", produces = "application/octet-stream")
    public ResponseEntity<byte[]> getZippedOutputsById(@PathVariable String id) {
        return getZippedGroup(id, MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE);
    }

    // TODO: To be removed, task-manager should expose timestamp instead
    private ResponseEntity<byte[]> getZippedGroup(String id, String fileGroup) {
        try {
            ByteArrayOutputStream zip = fileManager.getZippedGroupById(id, fileGroup);
            String zipName = fileGroup + ".zip";
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment;filename=\"" + zipName + "\"")
                .body(zip.toByteArray());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        } catch (TaskNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/tasks/businessdate/{businessDate}")
    public ResponseEntity<List<TaskDto>> getListTasksFromBusinessDate(@PathVariable String businessDate) {
        return ResponseEntity.ok().body(builder.getListTasksDto(LocalDate.parse(businessDate)));
    }
}
