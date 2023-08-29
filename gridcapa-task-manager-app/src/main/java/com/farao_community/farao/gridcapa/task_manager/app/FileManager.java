/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private static final DateTimeFormatter LOG_FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HH30");
    private static final String ZIP_EXTENSION = ".zip";
    private static final String TXT_EXTENSION = ".txt";
    private static final String RAO_LOGS_FILENAME = "rao_logs.txt";

    private final TaskRepository taskRepository;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final Logger businessLogger;
    private final MinioAdapter minioAdapter;

    public FileManager(TaskRepository taskRepository, TaskManagerConfigurationProperties taskManagerConfigurationProperties, Logger businessLogger, MinioAdapter minioAdapter) {
        this.taskRepository = taskRepository;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.businessLogger = businessLogger;
        this.minioAdapter = minioAdapter;
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

    public ByteArrayOutputStream getLogs(OffsetDateTime timestamp) throws IOException {
        Optional<Task> optTask = taskRepository.findByTimestamp(timestamp);
        if (optTask.isPresent()) {
            Task task = optTask.get();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(baos)) {
                addLogsFileToArchive(task, zos);
                return baos;
            }
        } else {
            throw new TaskNotFoundException();
        }
    }

    public ByteArrayOutputStream getRaoRunnerAppLogs(OffsetDateTime timestamp) throws IOException {
        Optional<Task> optTask = taskRepository.findByTimestamp(timestamp);
        if (optTask.isPresent()) {
            Task task = optTask.get();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry(generateLogFileName(timestamp)));
                writeToZipOutputStream(zos, getRaoRunnerAppLogsFile(task));
                return baos;
            }
        } else {
            throw new TaskNotFoundException();
        }
    }

    private String generateLogFileName(OffsetDateTime timestamp) {
        ZonedDateTime timestampInEuropeZone = timestamp.atZoneSameInstant(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone()));
        String dateAndTime = timestampInEuropeZone.format(LOG_FILENAME_DATE_TIME_FORMATTER);
        String output = dateAndTime + "_RAO-LOGS-1" + TXT_EXTENSION;
        return handle25TimestampCase(output, timestamp);
    }

    private String handle25TimestampCase(String filename, OffsetDateTime timestamp) {
        ZoneOffset previousOffset = OffsetDateTime.from(timestamp.toInstant().minus(1, ChronoUnit.HOURS).atZone(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone()))).getOffset();
        ZoneOffset currentOffset = OffsetDateTime.from(timestamp.toInstant().atZone(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone()))).getOffset();
        if (previousOffset == ZoneOffset.ofHours(2) && currentOffset == ZoneOffset.ofHours(1)) {
            return filename.replace("_0", "_B");
        } else {
            return filename;
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
            if (isExportLogsEnabledAndFileGroupIsGridcapaOutput(fileGroup)) {
                addLogsFileToArchive(task, zos);
            }
            return baos;
        }
    }

    boolean isExportLogsEnabledAndFileGroupIsGridcapaOutput(String fileGroup) {
        return taskManagerConfigurationProperties.getProcess().isExportLogsEnabled() &&
            fileGroup.equalsIgnoreCase(MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE);
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
        try (InputStream is = openUrlStream(minioAdapter.generatePreSignedUrl(processFile.getFileObjectKey()))) {
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

    private InputStream getRaoRunnerAppLogsFile(Task task) {
        SortedSet<ProcessEvent> events = task.getProcessEvents();
        TreeSet<ProcessEvent> treeSet =  new TreeSet<>(events);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (ProcessEvent event :  treeSet.descendingSet()) {
            if (event.getServiceName().equals("rao-runner-app")) {
                baos.writeBytes(event.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    public InputStream openUrlStream(String urlString) {
        try {
            if (taskManagerConfigurationProperties.getWhitelist().stream().noneMatch(urlString::startsWith)) {
                throw new TaskManagerException(String.format("URL '%s' is not part of application's whitelisted url's.", urlString));
            }
            URL url = new URL(urlString);
            return url.openStream(); // NOSONAR Usage of whitelist not triggered by Sonar quality assessment, even if listed as a solution to the vulnerability
        } catch (IOException e) {
            businessLogger.error("Error while retrieving content of file : {}, Link may have expired.", getFileNameFromUrl(urlString));
            throw new TaskManagerException(String.format("Exception occurred while retrieving file content from : %s Cause: %s ", urlString, e.getMessage()));
        }
    }

    private String getFileNameFromUrl(String stringUrl) {
        try {
            URL url = new URL(stringUrl);
            return FilenameUtils.getName(url.getPath());
        } catch (IOException e) {
            throw new TaskManagerException(String.format("Exception occurred while retrieving file name from : %s Cause: %s ", stringUrl, e.getMessage()));
        }
    }

    public String generatePresignedUrl(String minioUrl) {
        return minioAdapter.generatePreSignedUrlFromFullMinioPath(minioUrl, 1);
    }
}
