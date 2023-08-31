/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.service.StatusHandler;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Controller
@RequestMapping
public class TaskManagerController {

    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    private final StatusHandler statusHandler;
    private final TaskDtoBuilder builder;
    private final FileManager fileManager;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;

    public TaskManagerController(StatusHandler statusHandler, TaskDtoBuilder builder, FileManager fileManager, TaskManagerConfigurationProperties taskManagerConfigurationProperties) {
        this.statusHandler = statusHandler;
        this.builder = builder;
        this.fileManager = fileManager;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
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
        Optional<Task> optTask = statusHandler.handleTaskStatusUpdate(OffsetDateTime.parse(timestamp), taskStatus);
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
                    .header(CONTENT_DISPOSITION, "attachment;filename=\"" + zipName + "\"")
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

    @GetMapping(value = "/tasks/businessdate/{businessDate}/allOver")
    public ResponseEntity<Boolean> areAllTasksFromBusinessDateOver(@PathVariable String businessDate) {
        return ResponseEntity.ok().body(
            builder.getListTasksDto(LocalDate.parse(businessDate))
                .stream().map(TaskDto::getStatus)
                .allMatch(TaskStatus::isOver));
    }

    @GetMapping(value = "/tasks/runningtasks")
    public ResponseEntity<List<TaskDto>> getListRunningTasks() {
        return ResponseEntity.ok().body(builder.getListRunningTasksDto());
    }

    @GetMapping(value = "/tasks/{timestamp}/file/{fileType}", produces = "application/octet-stream")
    public ResponseEntity<byte[]> getFile(@PathVariable String fileType, @PathVariable String timestamp) throws IOException {
        ResponseEntity<byte[]> result = ResponseEntity.notFound().build();
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);
        TaskDto task = builder.getTaskDto(offsetDateTime);
        List<ProcessFileDto> allFiles = new ArrayList<>();
        allFiles.addAll(task.getInputs());
        allFiles.addAll(task.getOutputs());
        Optional<ProcessFileDto> myFile = allFiles.stream().filter(f -> f.getFileType().equals(fileType)).findFirst();
        if (myFile.isPresent()) {
            BufferedInputStream in = new BufferedInputStream(this.fileManager.openUrlStream(fileManager.generatePresignedUrl(myFile.get().getFilePath())));
            result = ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(CONTENT_DISPOSITION, "attachment;filename=\"" + myFile.get().getFilename() + "\"")
                    .body(IOUtils.toByteArray(in));
        } else if (taskManagerConfigurationProperties.getProcess().isExportLogsEnabled() && StringUtils.equalsIgnoreCase("LOGS", fileType)) {
            String fileNameLocalDateTime = offsetDateTime.atZoneSameInstant(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone())).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));
            result = ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(CONTENT_DISPOSITION, "attachment;filename=\"rao_logs_" + fileNameLocalDateTime + ".zip\"")
                    .body(fileManager.getLogs(offsetDateTime).toByteArray());
        }
        return result;
    }

    @GetMapping(value = "/tasks/{timestamp}/log", produces = "application/octet-stream")
    public ResponseEntity<byte[]> getLog(@PathVariable String timestamp) throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(CONTENT_DISPOSITION, "attachment;filename=\"rao_logs_" + removeIllegalUrlCharacter(timestamp) + ".zip\"")
                .body(fileManager.getRaoRunnerAppLogs(OffsetDateTime.parse(timestamp)).toByteArray());
    }

    private String removeIllegalUrlCharacter(String s) {
        return s.replace(":", "");
    }
}
