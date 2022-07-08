/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.apache.commons.io.IOUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Controller
@RequestMapping
public class TaskManagerController {

    private final TaskDtoBuilder builder;
    private final TaskManager taskManager;
    private final FileManager fileManager;

    private final UrlValidationService urlValidationService;

    public TaskManagerController(TaskDtoBuilder builder, TaskManager taskManager, FileManager fileManager, UrlValidationService urlValidationService) {
        this.builder = builder;
        this.taskManager = taskManager;
        this.fileManager = fileManager;
        this.urlValidationService = urlValidationService;
    }

    @GetMapping(value = "/tasks/{timestamp}")
    public ResponseEntity<TaskDto> getTaskFromTimestamp(@PathVariable String timestamp) {
        return ResponseEntity.ok().body(builder.getTaskDto(OffsetDateTime.parse(timestamp)));
    }

    @PutMapping(value = "/tasks/{timestamp}/status")
    public ResponseEntity<TaskDto> updateStatus(@PathVariable String timestamp, @RequestParam String status) {
        TaskStatus taskStatus;
        try {
            taskStatus = TaskStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Task> optTask = taskManager.handleTaskStatusUpdate(OffsetDateTime.parse(timestamp), taskStatus);
        return optTask.map(task -> ResponseEntity.ok(builder.createDtoFromEntity(task)))
            .orElseGet(() -> ResponseEntity.notFound().build());
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
            String zipName = fileManager.getZipName(timestamp, fileGroup);
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

    @GetMapping(value = "/file/{fileType}/{timestamp}", produces = "application/octet-stream")
    public @ResponseBody ResponseEntity<byte[]> getFile(@PathVariable String fileType, @PathVariable String timestamp) throws IOException {
        ResponseEntity<byte[]> result = ResponseEntity.notFound().build();
        TaskDto task = builder.getTaskDto(OffsetDateTime.parse(timestamp));
        List<ProcessFileDto> allFiles = new ArrayList<>();
        allFiles.addAll(task.getInputs());
        allFiles.addAll(task.getOutputs());
        Optional<ProcessFileDto> myFile = allFiles.stream().filter(f -> f.getFileType().equals(fileType)).findFirst();
        if (myFile.isPresent()) {
            BufferedInputStream in = new BufferedInputStream(this.urlValidationService.openUrlStream(myFile.get().getFileUrl()));
            result = ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment;filename=\"" + myFile.get().getFilename() + "\"")
                    .body(IOUtils.toByteArray(in));
        }

        return result;

    }
}
