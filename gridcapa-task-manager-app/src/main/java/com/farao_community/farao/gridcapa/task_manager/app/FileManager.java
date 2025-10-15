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
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.HOURS;

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
    public static final int OFFSET_BEFORE_WINTER_DST = 2;
    public static final int OFFSET_AFTER_WINTER_DST = 1;

    private final TaskRepository taskRepository;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final Logger businessLogger;
    private final MinioAdapter minioAdapter;

    public FileManager(final TaskRepository taskRepository,
                       final TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                       final Logger businessLogger,
                       final MinioAdapter minioAdapter) {
        this.taskRepository = taskRepository;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.businessLogger = businessLogger;
        this.minioAdapter = minioAdapter;
    }

    public ByteArrayOutputStream getZippedGroup(final OffsetDateTime timestamp, final String fileGroup) throws IOException {
        final Optional<Task> optTask = taskRepository.findByTimestampAndFetchProcessEvents(timestamp);
        if (optTask.isPresent()) {
            Task task = optTask.get();
            return getZippedFileGroup(task, fileGroup);
        } else {
            throw new TaskNotFoundException();
        }
    }

    public ByteArrayOutputStream getZippedGroupById(final String id, final String fileGroup) throws IOException {
        Optional<Task> optTask = taskRepository.findById(UUID.fromString(id));
        if (optTask.isPresent()) {
            Task task = optTask.get();
            return getZippedFileGroup(task, fileGroup);
        } else {
            throw new TaskNotFoundException();
        }
    }

    public ByteArrayOutputStream getLogs(final OffsetDateTime timestamp) throws IOException {
        Optional<Task> optTask = taskRepository.findByTimestampAndFetchProcessEvents(timestamp);
        if (optTask.isPresent()) {
            Task task = optTask.get();
            try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
                 final ZipOutputStream zos = new ZipOutputStream(os)) {
                addLogsFileToArchive(task, zos);
                return os;
            }
        } else {
            throw new TaskNotFoundException();
        }
    }

    public ByteArrayOutputStream getRaoRunnerAppLogs(OffsetDateTime timestamp) throws IOException {
        final Optional<Task> optTask = taskRepository.findByTimestampAndFetchProcessEvents(timestamp);
        if (optTask.isPresent()) {
            final Task task = optTask.get();
            try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
                 final ZipOutputStream zos = new ZipOutputStream(os)) {
                zos.putNextEntry(new ZipEntry(generateLogFileName(timestamp)));
                writeToZipOutputStream(zos, getRaoRunnerAppLogsFile(task));
                return os;
            }
        } else {
            throw new TaskNotFoundException();
        }
    }

    private String generateLogFileName(final OffsetDateTime timestamp) {
        final ZonedDateTime timestampInEuropeZone = timestamp.atZoneSameInstant(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone()));
        String dateAndTime = timestampInEuropeZone.format(LOG_FILENAME_DATE_TIME_FORMATTER);
        String output = dateAndTime + "_RAO-LOGS-1" + TXT_EXTENSION;
        return handleWinterDst(output, timestamp);
    }

    private String handleWinterDst(final String filename, final OffsetDateTime timestamp) {
        final ZoneId zoneId = ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone());
        final Instant instant = timestamp.toInstant();
        final ZoneOffset previousOffset = OffsetDateTime.from(instant.minus(1, HOURS)
                                                            .atZone(zoneId)).getOffset();
        final ZoneOffset currentOffset = OffsetDateTime.from(instant.atZone(zoneId)).getOffset();

        if (previousOffset.equals(ZoneOffset.ofHours(OFFSET_BEFORE_WINTER_DST))
                && currentOffset.equals(ZoneOffset.ofHours(OFFSET_AFTER_WINTER_DST))) {
            return filename.replace("_0", "_B");
        } else {
            return filename;
        }
    }

    public String getZipName(final OffsetDateTime timestamp, final String fileGroup) {
        return timestamp.atZoneSameInstant(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone()))
                   .format(ZIP_DATE_TIME_FORMATTER) + "_" + fileGroup + ZIP_EXTENSION;
    }

    private ByteArrayOutputStream getZippedFileGroup(final Task task, final String fileGroup) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(os)) {
            final Set<ProcessFile> groupProcessFiles = getProcessFiles(task, fileGroup);
            for (final ProcessFile processFile : groupProcessFiles) {
                writeZipEntry(zos, processFile);
            }
            if (areLogsExportable(fileGroup)) {
                addLogsFileToArchive(task, zos);
            }
            return os;
        }
    }

    protected boolean areLogsExportable(final String fileGroup) {
        return taskManagerConfigurationProperties.getProcess().isExportLogsEnabled() &&
            fileGroup.equalsIgnoreCase(DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE);
    }

    private Set<ProcessFile> getProcessFiles(final Task task, final String fileGroup) {
        return task.getProcessFiles().stream()
            .filter(processFile -> processFile.getFileGroup().equals(fileGroup))
            .collect(Collectors.toSet());
    }

    private void addLogsFileToArchive(final Task task, final ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(RAO_LOGS_FILENAME));
        writeToZipOutputStream(zos, getLogsFile(task));
    }

    private void writeZipEntry(final ZipOutputStream zos, final ProcessFile processFile) throws IOException {
        try (final InputStream is = openUrlStream(minioAdapter.generatePreSignedUrl(processFile.getFileObjectKey()))) {
            zos.putNextEntry(new ZipEntry(processFile.getFilename()));
            writeToZipOutputStream(zos, is);
        }
    }

    private void writeToZipOutputStream(final ZipOutputStream zos, final InputStream is) throws IOException {
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            zos.write(buffer, 0, len);
        }
    }

    private InputStream getLogsFile(final Task task) {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        task.getProcessEvents()
            .stream()
            .map(ProcessEvent::toString)
            .map(s -> s.getBytes(UTF_8))
            .forEach(os::writeBytes);

        return new ByteArrayInputStream(os.toByteArray());
    }

    private InputStream getRaoRunnerAppLogsFile(final Task task) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        new TreeSet<>(task.getProcessEvents()).descendingSet()
            .stream()
            .filter(e -> e.getServiceName().equals("rao-runner-app"))
            .map(ProcessEvent::toString)
            .map(s -> s.getBytes(UTF_8))
            .forEach(os::writeBytes);

        return new ByteArrayInputStream(os.toByteArray());
    }

    public InputStream openUrlStream(final String urlString) {
        try {
            if (taskManagerConfigurationProperties.getWhitelist().stream().noneMatch(urlString::startsWith)) {
                throw new TaskManagerException(String.format("URL '%s' is not part of application's whitelisted url's.", urlString));
            }
            final URL url = new URI(urlString).toURL();
            return url.openStream(); // NOSONAR Usage of whitelist not triggered by Sonar quality assessment, even if listed as a solution to the vulnerability
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            businessLogger.error("Error while retrieving content of file \"{}\", link may have expired.", getFileNameFromUrl(urlString));
            throw new TaskManagerException(String.format("Exception occurred while retrieving file content from %s", urlString), e);
        }
    }

    private String getFileNameFromUrl(final String stringUrl) {
        try {
            final URL url = new URI(stringUrl).toURL();
            return FilenameUtils.getName(url.getPath());
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw new TaskManagerException(String.format("Exception occurred while retrieving file name from : %s", stringUrl), e);
        }
    }

    public String generatePresignedUrl(final String minioUrl) {
        return minioAdapter.generatePreSignedUrlFromFullMinioPath(minioUrl, 1);
    }

    public void uploadFileToMinio(final OffsetDateTime timestamp, final MultipartFile file,
                                  final String fileType, final String fileName) {
        final String processTag = taskManagerConfigurationProperties.getProcess().getTag();
        final String path = String.format("%s/MANUAL_UPLOAD/%s/%s",
                                    taskManagerConfigurationProperties.getProcess().getManualUploadBasePath(),
                                    timestamp.format(ZIP_DATE_TIME_FORMATTER),
                                    fileName);
        try (final InputStream in = file.getInputStream()) {
            minioAdapter.uploadInputForTimestamp(path, in, processTag, fileType, timestamp);
        } catch (IOException e) {
            throw new TaskManagerException(String.format("Exception occurred while uploading file to minio : %s", file.getName()), e);
        }
    }

}
