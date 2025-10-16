/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFileMinio;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.entities.TaskWithStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessFileRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import io.minio.messages.Event;
import io.minio.messages.NotificationRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.net.URLDecoder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.PENDING;
import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.RUNNING;
import static com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties.TASK_MANAGER_LOCK;
import static com.farao_community.farao.gridcapa.task_manager.app.entities.FileEventType.AVAILABLE;
import static com.farao_community.farao.gridcapa.task_manager.app.entities.FileEventType.UPDATED;
import static com.farao_community.farao.gridcapa.task_manager.app.entities.FileEventType.WAITING;
import static com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Service
public class MinioHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinioHandler.class);
    public static final String FILE_GROUP_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_GROUP_METADATA_KEY;
    public static final String FILE_TARGET_PROCESS_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TARGET_PROCESS_METADATA_KEY;
    public static final String FILE_TYPE_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TYPE_METADATA_KEY;
    public static final String DOCUMENT_ID_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_DOCUMENT_ID_METADATA_KEY;
    public static final String FILE_VALIDITY_INTERVAL_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_VALIDITY_INTERVAL_METADATA_KEY;
    private static final String PROCESS_FILE_REMOVED_MESSAGE = "process file {} was removed from waiting list";

    private final ProcessFileRepository processFileRepository;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final TaskUpdateNotifier taskUpdateNotifier;
    private final List<ProcessFileMinio> waitingFilesList = new ArrayList<>();

    public MinioHandler(final ProcessFileRepository processFileRepository,
                        final TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                        final TaskRepository taskRepository,
                        final TaskService taskService,
                        final TaskUpdateNotifier taskUpdateNotifier) {
        this.processFileRepository = processFileRepository;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.taskUpdateNotifier = taskUpdateNotifier;
    }

    @Bean
    @Transactional
    public Consumer<Flux<NotificationRecords>> consumeMinioEvent() {
        return f -> f.subscribe(nr -> {
            try {
                handleMinioEvent(nr);
            } catch (Exception e) {
                LOGGER.error("Unable to handle MinIO event properly", e);
            }
        });
    }

    public void handleMinioEvent(final NotificationRecords notificationRecords) {
        notificationRecords.events().forEach(event -> {
            LOGGER.debug("s3 event received");
            switch (event.eventType()) {
                case OBJECT_CREATED_ANY,
                     OBJECT_CREATED_PUT,
                     OBJECT_CREATED_POST,
                     OBJECT_CREATED_COPY,
                     OBJECT_CREATED_COMPLETE_MULTIPART_UPLOAD -> updateTasks(event);
                case OBJECT_REMOVED_ANY,
                     OBJECT_REMOVED_DELETE,
                     OBJECT_REMOVED_DELETED_MARKER_CREATED -> removeProcessFile(event);
                default -> LOGGER.info("S3 event type {} not handled by task manager", event.eventType());
            }
        });
    }

    public void updateTasks(final Event event) {
        synchronized (TASK_MANAGER_LOCK) {
            final Map<String, String> metadata = event.userMetadata();
            if (!metadata.isEmpty()
                && taskManagerConfigurationProperties.getProcess().getTag()
                        .equals(metadata.get(FILE_TARGET_PROCESS_METADATA_KEY))) {
                final ProcessFileMinio processFileMinio = buildProcessFileMinioFromEvent(event);
                if (processFileMinio != null) {
                    ProcessFile processFile = processFileMinio.getProcessFile();
                    if (!processFile.isInputFile()) {
                        processFile = processFileRepository.save(processFile);
                        Set<TaskWithStatusUpdate> taskWithStatusUpdates = taskService.addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), false, false);
                        saveAndNotifyTasks(taskWithStatusUpdates, false);
                        LOGGER.info("Process file {} has been added properly", processFile.getFilename());
                    } else {
                        // If the file coming is an input while one of the concerned timestamp is running, the file put in a waiting list until the process ends
                        final Set<Task> runningOrPendingTasks = taskRepository.findAllByTimestampBetweenAndStatusIn(processFile.getStartingAvailabilityDate(),
                                                                                                                    processFile.getEndingAvailabilityDate(),
                                                                                                                    Set.of(RUNNING, PENDING));
                        if (!runningOrPendingTasks.isEmpty()) {
                            addWaitingFileAndNotifyTasks(processFileMinio, runningOrPendingTasks);
                        } else {
                            processFile = processFileRepository.save(processFile);
                            Set<TaskWithStatusUpdate> taskWithStatusUpdates = taskService.addProcessFileToTasks(processFile,
                                                                                                                processFileMinio.getFileEventType(),
                                                                                                                true,
                                                                                                                true);
                            saveAndNotifyTasks(taskWithStatusUpdates, true);
                            LOGGER.info("Process file {} has been added properly", processFile.getFilename());
                        }
                    }
                } else {
                    LOGGER.warn("Minio object {} has not been added ", URLDecoder.decode(event.objectName(), UTF_8));
                }
            }
        }
    }

    private ProcessFileMinio buildProcessFileMinioFromEvent(final Event event) {
        final Map<String, String> metadata = event.userMetadata();
        final String validityInterval = metadata.get(FILE_VALIDITY_INTERVAL_METADATA_KEY);
        final String objectKey = URLDecoder.decode(event.objectName(), UTF_8);
        if (!isEmpty(validityInterval)) {
            final String fileGroup = metadata.get(FILE_GROUP_METADATA_KEY);
            final String fileType = metadata.get(FILE_TYPE_METADATA_KEY);
            final String documentId = metadata.get(DOCUMENT_ID_METADATA_KEY);
            LOGGER.info("Adding MinIO object {}", objectKey);
            final String[] interval = validityInterval.split("/");
            return getProcessFileMinio(
                    OffsetDateTime.parse(interval[0]),
                    OffsetDateTime.parse(interval[1]),
                    objectKey,
                    fileType,
                    fileGroup,
                    documentId);
        } else {
            return null;
        }
    }

    protected ProcessFileMinio getProcessFileMinio(final OffsetDateTime startTime,
                                                   final OffsetDateTime endTime,
                                                   final String objectKey,
                                                   final String fileType,
                                                   final String fileGroup,
                                                   final String documentId) {
        /*
        This implies that only one file per type and group can exist. If another one is imported it would just
        replace the previous one.
        */
        final Optional<ProcessFile> optProcessFile;
        if (DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(fileGroup)) {
            optProcessFile = processFileRepository.findByFileObjectKey(objectKey);
        } else {
            optProcessFile = processFileRepository.findByStartingAvailabilityDateAndFileTypeAndGroup(startTime, fileType, fileGroup);
        }

        if (optProcessFile.isPresent()) {
            LOGGER.info("File {} available at {} is already referenced in the database. Updating process file data.",
                        fileType, startTime);
            final ProcessFile processFile = optProcessFile.get();
            processFile.setFileObjectKey(objectKey);
            processFile.setLastModificationDate(getTimestampNowWithProcessTimezone());
            processFile.setDocumentId(documentId);
            return new ProcessFileMinio(processFile, UPDATED);
        } else {
            LOGGER.info("Creating a new file {} available at {}.", fileType, startTime);
            final ProcessFile processFile = new ProcessFile(objectKey, fileGroup, fileType, documentId,
                                                            startTime, endTime, getTimestampNowWithProcessTimezone());
            return new ProcessFileMinio(processFile, AVAILABLE);
        }
    }

    private OffsetDateTime getTimestampNowWithProcessTimezone() {
        return OffsetDateTime.now(taskManagerConfigurationProperties.getProcessTimezone());
    }

    private void addWaitingFileAndNotifyTasks(ProcessFileMinio processFileMinio,
                                              Set<Task> runningOrPendingTasks) {
        for (final Task task : runningOrPendingTasks) {
            removeWaitingFileWithSameTypeAndValidity(processFileMinio);
            waitingFilesList.add(processFileMinio);
            LOGGER.info("process file {} is added to waiting files list", processFileMinio.getProcessFile().getFilename());
            taskService.addFileEventToTask(task, WAITING, processFileMinio.getProcessFile(), "WARN");
            saveAndNotifyTasks(Collections.singleton(new TaskWithStatusUpdate(task, false)), false); //No need to update status when the file is waiting
        }
    }

    private static void logDeletion(final String fileName) {
        LOGGER.info(PROCESS_FILE_REMOVED_MESSAGE, fileName);
    }

    private void removeWaitingFileWithSameTypeAndValidity(ProcessFileMinio newProcessFileMinio) {
        final Set<ProcessFileMinio> processFileMiniosWaiting = waitingFilesList.stream()
                .filter(newProcessFileMinio::hasSameTypeAndValidity)
                .collect(Collectors.toSet());
        for (final ProcessFileMinio processFileMinio : processFileMiniosWaiting) {
            waitingFilesList.remove(processFileMinio);
            logDeletion(processFileMinio.getProcessFile().getFilename());
        }
    }

    public void emptyWaitingList(final OffsetDateTime timestamp) {
        LOGGER.info("Handle Emptying of waiting list..");
        LOGGER.info("Waiting list contains {} files", waitingFilesList.size());
        final List<ProcessFileMinio> waitingProcessFilesToAdd = getWaitingProcessFilesForTimestamp(timestamp);
        int nbOfWaitingFiles = waitingProcessFilesToAdd.size();

        if (nbOfWaitingFiles >= 1) {
            // each process file is added to the task, but the status is updated only for the last file waiting (withStatusUpdate = true parameter)
            for (int i = 0; i < nbOfWaitingFiles - 1; i++) {
                ProcessFileMinio processFileMinio = waitingProcessFilesToAdd.get(i);
                ProcessFile processFile = processFileRepository.save(processFileMinio.getProcessFile());
                Set<TaskWithStatusUpdate> tasksWithStatusUpdate = taskService.addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), true, false);
                saveAndNotifyTasks(tasksWithStatusUpdate, processFile.isInputFile());
                waitingFilesList.remove(processFileMinio);
                logDeletion(processFile.getFilename());
            }
            ProcessFileMinio lastProcessFileMinio = waitingProcessFilesToAdd.get(nbOfWaitingFiles - 1);
            final ProcessFile lastProcessFile = processFileRepository.save(lastProcessFileMinio.getProcessFile());
            final Set<TaskWithStatusUpdate> tasksWithStatusUpdate = taskService.addProcessFileToTasks(lastProcessFile,
                                                                                                      lastProcessFileMinio.getFileEventType(),
                                                                                                      true, true);
            saveAndNotifyTasks(tasksWithStatusUpdate, lastProcessFile.isInputFile());
            waitingFilesList.remove(lastProcessFileMinio);
            logDeletion(lastProcessFile.getFilename());
        }
    }

    List<ProcessFileMinio> getWaitingProcessFilesForTimestamp(final OffsetDateTime timestamp) {
        final List<ProcessFileMinio> processFilesWithFinishedTasks = new ArrayList<>();
        for (final ProcessFileMinio processFileMinio : waitingFilesList) {
            final ProcessFile processFile = processFileMinio.getProcessFile();
            if (isFileValidForTimestamp(timestamp, processFile)
                && taskRepository.findAllByTimestampBetweenAndStatusIn(processFile.getStartingAvailabilityDate(),
                                                                       processFile.getEndingAvailabilityDate(),
                                                                       Set.of(RUNNING, PENDING)).isEmpty()) {
                processFilesWithFinishedTasks.add(processFileMinio);
                LOGGER.info("process file to add {} for timestamp {}", processFileMinio.getProcessFile().getFilename(), timestamp);
            }
        }
        return processFilesWithFinishedTasks;
    }

    private boolean isFileValidForTimestamp(final OffsetDateTime timestamp,
                                            final ProcessFile processFile) {
        final OffsetDateTime startingAvailabilityDate = processFile.getStartingAvailabilityDate();
        final OffsetDateTime endingAvailabilityDate = processFile.getEndingAvailabilityDate();
        return !timestamp.isBefore(startingAvailabilityDate) && timestamp.isBefore(endingAvailabilityDate);
    }

    public void removeProcessFile(final Event event) {
        synchronized (TASK_MANAGER_LOCK) {
            String objectKey = URLDecoder.decode(event.objectName(), UTF_8);
            LOGGER.info("Removing MinIO object {}", objectKey);
            final Optional<ProcessFile> optionalProcessFile = processFileRepository.findByFileObjectKey(objectKey);
            if (optionalProcessFile.isPresent()) {
                final ProcessFile processFile = optionalProcessFile.get();
                LOGGER.debug("Finding tasks related to {}", processFile.getFilename());
                saveAndNotifyTasks(taskService.removeProcessFileFromTasks(processFile), false);
                processFileRepository.delete(processFile);
                LOGGER.info("Process file {} has been removed properly", processFile.getFilename());
            } else {
                LOGGER.info("File not referenced in the database. Nothing to do.");
            }
        }
    }

    private void saveAndNotifyTasks(Set<TaskWithStatusUpdate> taskWithStatusUpdateSet,
                                    boolean withNewInput) {
        LOGGER.debug("Saving related tasks in DB");
        taskRepository.saveAllAndFlush(taskWithStatusUpdateSet.stream().map(TaskWithStatusUpdate::getTask).toList());
        LOGGER.debug("Notifying on web-sockets");
        if (withNewInput) {
            taskUpdateNotifier.notifyNewInput(taskWithStatusUpdateSet);
        } else {
            taskUpdateNotifier.notify(taskWithStatusUpdateSet);
        }
    }
}
