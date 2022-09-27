/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class FileManager {

    private static final DateTimeFormatter ZIP_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH30");
    private static final String ZIP_EXTENSION = ".zip";
    private static final String CSE_EXPORT_COMMON_PART = "CSE_EXPORT";
    private static final String RAO_LOGS_FILENAME = "rao_logs.txt";

    private final TaskRepository taskRepository;
    private final UrlValidationService urlValidationService;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;

    public FileManager(TaskRepository taskRepository, UrlValidationService urlValidationService, TaskManagerConfigurationProperties taskManagerConfigurationProperties) {
        this.taskRepository = taskRepository;
        this.urlValidationService = urlValidationService;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
    }

    public ByteArrayOutputStream getZippedGroup(OffsetDateTime timestamp, String fileGroup) throws IOException {
        Optional<Task> optTask = taskRepository.findByTimestamp(timestamp);
        if (optTask.isPresent()) {
            Task task = optTask.get();
            return getZippedFileGroup(task, fileGroup);
        } else {
            throw new TaskNotFoundException();
        }
    }

    public ByteArrayOutputStream getZippedGroupById(String id, String fileGroup) throws IOException {
        Optional<Task> optTask = taskRepository.findById(UUID.fromString(id));
        if (optTask.isPresent()) {
            Task task = optTask.get();
            return getZippedFileGroup(task, fileGroup);
        } else {
            throw new TaskNotFoundException();
        }
    }

    String getZipName(OffsetDateTime timestamp, String fileGroup) {
        return timestamp.atZoneSameInstant(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone())).format(ZIP_DATE_TIME_FORMATTER) + "_" + fileGroup + ZIP_EXTENSION;
    }

    private ByteArrayOutputStream getZippedFileGroup(Task task, String fileGroup) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Set<ProcessFile> groupProcessFiles = getProcessFiles(task, fileGroup);
            for (ProcessFile processFile : groupProcessFiles) {
                writeZipEntry(zos, processFile);
            }
            if (taskManagerConfigurationProperties.getProcess().getTag().contains(CSE_EXPORT_COMMON_PART) &&
                    fileGroup.equalsIgnoreCase(MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE)) {
                addLogsFileToArchive(task, zos);
            }
            return baos;
        }
    }

    private Set<ProcessFile> getProcessFiles(Task task, String fileGroup) {
        return task.getProcessFiles().stream()
            .filter(processFile -> processFile.getFileGroup().equals(fileGroup))
            .collect(Collectors.toSet());
    }

    private void addLogsFileToArchive(Task task, ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(RAO_LOGS_FILENAME));
        writeToZipOutputStream(zos, getLogsFile(task));
    }

    private void writeZipEntry(ZipOutputStream zos, ProcessFile processFile) throws IOException {
        try (InputStream is = urlValidationService.openUrlStream(processFile.getFileUrl())) {
            zos.putNextEntry(new ZipEntry(processFile.getFilename()));
            writeToZipOutputStream(zos, is);
        }
    }

    private void writeToZipOutputStream(ZipOutputStream zos, InputStream is) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            zos.write(buffer, 0, len);
        }
    }

    private InputStream getLogsFile(Task task) {
        SortedSet<ProcessEvent> events = task.getProcessEvents();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (ProcessEvent event : events) {
            baos.writeBytes(event.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }
}
