/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
            return baos;
        }
    }

    private Set<ProcessFile> getProcessFiles(Task task, String fileGroup) {
        return task.getProcessFiles().stream()
            .filter(processFile -> processFile.getFileGroup().equals(fileGroup))
            .collect(Collectors.toSet());
    }

    private void writeZipEntry(ZipOutputStream zos, ProcessFile processFile) throws IOException {
        try (InputStream is = urlValidationService.openUrlStream(processFile.getFileUrl())) {
            zos.putNextEntry(new ZipEntry(processFile.getFilename()));
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }
    }
}
