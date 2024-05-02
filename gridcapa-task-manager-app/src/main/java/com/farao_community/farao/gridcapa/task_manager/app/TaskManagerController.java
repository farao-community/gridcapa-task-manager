/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.ParameterDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.service.FileSelectorService;
import com.farao_community.farao.gridcapa.task_manager.app.service.ParameterService;
import com.farao_community.farao.gridcapa.task_manager.app.service.StatusHandler;
import com.farao_community.farao.gridcapa.task_manager.app.service.TaskDtoBuilderService;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

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
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Marc Schwitzguebel {@literal <marc.schwitzguebel at rte-france.com>}
 */

@Controller
@RequestMapping
public class TaskManagerController {

    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    private final StatusHandler statusHandler;
    private final TaskDtoBuilderService builder;
    private final FileSelectorService fileSelectorService;
    private final FileManager fileManager;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final Logger businessLogger;
    private final ParameterService parameterService;

    public TaskManagerController(StatusHandler statusHandler, TaskDtoBuilderService builder, FileSelectorService fileSelectorService, FileManager fileManager, TaskManagerConfigurationProperties taskManagerConfigurationProperties, Logger businessLogger, ParameterService parameterService) {
        this.statusHandler = statusHandler;
        this.builder = builder;
        this.fileSelectorService = fileSelectorService;
        this.fileManager = fileManager;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.businessLogger = businessLogger;
        this.parameterService = parameterService;
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

    @PutMapping(value = "/tasks/{timestamp}/input/{filetype}")
    public ResponseEntity<String> selectFile(@PathVariable String timestamp, @PathVariable String filetype, @RequestParam String filename) {
        try {
            fileSelectorService.selectFile(OffsetDateTime.parse(timestamp), filetype, filename);
        } catch (final TaskNotFoundException | ProcessFileNotFoundException notFoundException) {
            return ResponseEntity.notFound().build();
        } catch (final TaskManagerException taskManagerException) {
            return ResponseEntity.badRequest().body(taskManagerException.getMessage());
        }
        return ResponseEntity.ok().build();
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

    @PostMapping(value = "/tasks/{timestamp}/export")
    public ResponseEntity<Object> triggerExport(@PathVariable String timestamp) {
        Optional<Task> optTask = Optional.empty();
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);

        TaskDto taskDto = builder.getTaskDto(offsetDateTime);
        TaskStatus taskStatus = taskDto.getStatus();
        if (TaskStatus.SUCCESS.equals(taskStatus)
                || TaskStatus.ERROR.equals(taskStatus)) {
            optTask = statusHandler.handleTaskStatusUpdate(offsetDateTime, taskStatus);
        }

        if (optTask.isPresent()) {
            MDC.put("gridcapa-task-id", optTask.get().getId().toString());
            businessLogger.info("Export of output files has been requested manually");
            return ResponseEntity.ok().build();
        } else {
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

    @PostMapping(value = "/tasks/{timestamp}/uploadfile")
    public ResponseEntity<Object> uploadFile(@PathVariable String timestamp,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam("fileName") String fileName,
                                             @RequestParam("fileType") String fileType
    ) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);
        try {
            fileManager.uploadFileToMinio(offsetDateTime, file, fileType, fileName);
        } catch (TaskManagerException tme) {
            businessLogger.error("Failed manually uploading file", tme);
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/parameters")
    public ResponseEntity<List<ParameterDto>> getParameters() {
        List<ParameterDto> parameters = parameterService.getParameters();
        return ResponseEntity.ok(parameters);
    }

    @PatchMapping(value = "/parameters")
    public ResponseEntity<Object> setParameterValues(@RequestBody List<ParameterDto> parameterDtos) {
        try {
            List<ParameterDto> updatedParameterDtos = parameterService.setParameterValues(parameterDtos);
            if (updatedParameterDtos.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updatedParameterDtos);
        } catch (TaskManagerException tme) {
            return ResponseEntity.badRequest().body(tme.getMessage());
        }
    }
}
