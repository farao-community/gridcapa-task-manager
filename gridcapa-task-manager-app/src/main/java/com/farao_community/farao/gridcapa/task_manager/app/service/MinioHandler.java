/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.FileEventType;
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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties.TASK_MANAGER_LOCK;

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
    public static final String FILE_VALIDITY_INTERVAL_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_VALIDITY_INTERVAL_METADATA_KEY;
    private static final String PROCESS_FILE_REMOVED_MESSAGE = "process file {} was removed from waiting list";

    private final ProcessFileRepository processFileRepository;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final TaskUpdateNotifier taskUpdateNotifier;
    private final List<ProcessFileMinio> waitingFilesList = new ArrayList<>();

    public MinioHandler(ProcessFileRepository processFileRepository, TaskManagerConfigurationProperties taskManagerConfigurationProperties, TaskRepository taskRepository, TaskService taskService, TaskUpdateNotifier taskUpdateNotifier) {
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

    @Transactional
    public void handleMinioEvent(NotificationRecords notificationRecords) {
        notificationRecords.events().forEach(event -> {
            LOGGER.debug("s3 event received");
            switch (event.eventType()) {
                case OBJECT_CREATED_ANY,
                     OBJECT_CREATED_PUT,
                     OBJECT_CREATED_POST,
                     OBJECT_CREATED_COPY,
                     OBJECT_CREATED_COMPLETE_MULTIPART_UPLOAD ->
                        updateTasks(event);
                case OBJECT_REMOVED_ANY,
                     OBJECT_REMOVED_DELETE,
                     OBJECT_REMOVED_DELETED_MARKER_CREATED ->
                        removeProcessFile(event);
                default -> LOGGER.info("S3 event type {} not handled by task manager", event.eventType());
            }
        });
    }

    @Transactional
    public void updateTasks(Event event) {
        synchronized (TASK_MANAGER_LOCK) {
            if (!event.userMetadata().isEmpty() && taskManagerConfigurationProperties.getProcess().getTag().equals(event.userMetadata().get(FILE_TARGET_PROCESS_METADATA_KEY))) {
                ProcessFileMinio processFileMinio = buildProcessFileMinioFromEvent(event);
                if (processFileMinio != null) {
                    ProcessFile processFile = processFileMinio.getProcessFile();
                    if (!processFile.isInputFile()) {
                        processFile = processFileRepository.save(processFile);
                        Set<TaskWithStatusUpdate> taskWithStatusUpdates = taskService.addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), false, false);
                        saveAndNotifyTasks(taskWithStatusUpdates);
                        LOGGER.info("Process file {} has been added properly", processFile.getFilename());
                    } else {
                        // If the file coming is an input while one of the concerned timestamp is running, the file put in a waiting list until the process ends
                        Set<Task> tasksForProcessFile = taskRepository.findAllByTimestampBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate());
                        if (isAnyTaskRunningOrPending(tasksForProcessFile)) {
                            addWaitingFileAndNotifyTasks(processFileMinio, tasksForProcessFile);
                        } else {
                            processFile = processFileRepository.save(processFile);
                            Set<TaskWithStatusUpdate> taskWithStatusUpdates = taskService.addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), true, true);
                            // TODO Explain why set value to true
                            taskWithStatusUpdates.stream().forEach(t -> t.setStatusUpdated(true));
                            saveAndNotifyTasks(taskWithStatusUpdates);
                            LOGGER.info("Process file {} has been added properly", processFile.getFilename());
                        }
                    }
                } else {
                    String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
                    LOGGER.warn("Minio object {} has not been added ", objectKey);
                }
            }
        }
    }

    boolean isAnyTaskRunningOrPending(Set<Task> tasks) {
        return tasks.stream().anyMatch(task -> task.getStatus().equals(TaskStatus.RUNNING) || task.getStatus().equals(TaskStatus.PENDING));
    }

    private ProcessFileMinio buildProcessFileMinioFromEvent(Event event) {
        String validityInterval = event.userMetadata().get(FILE_VALIDITY_INTERVAL_METADATA_KEY);
        String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
        if (validityInterval != null && !validityInterval.isEmpty()) {
            String fileGroup = event.userMetadata().get(FILE_GROUP_METADATA_KEY);
            String fileType = event.userMetadata().get(FILE_TYPE_METADATA_KEY);
            LOGGER.info("Adding MinIO object {}", objectKey);
            String[] interval = validityInterval.split("/");
            return getProcessFileMinio(
                    OffsetDateTime.parse(interval[0]),
                    OffsetDateTime.parse(interval[1]),
                    objectKey,
                    fileType,
                    fileGroup);
        } else {
            return null;
        }
    }

    ProcessFileMinio getProcessFileMinio(OffsetDateTime startTime, OffsetDateTime endTime, String objectKey, String fileType, String fileGroup) {
        /*
        This implies that only one file per type and group can exist. If another one is imported it would just
        replace the previous one.
        */
        Optional<ProcessFile> optProcessFile;
        if (MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(fileGroup)) {
            optProcessFile = processFileRepository.findByFileObjectKey(objectKey);
        } else {
            optProcessFile = processFileRepository.findByStartingAvailabilityDateAndFileTypeAndGroup(startTime, fileType, fileGroup);
        }

        if (optProcessFile.isPresent()) {
            LOGGER.info("File {} available at {} is already referenced in the database. Updating process file data.", fileType, startTime);
            ProcessFile processFile = optProcessFile.get();
            processFile.setFileObjectKey(objectKey);
            processFile.setLastModificationDate(getTimestampNowWithProcessTimezone());
            return new ProcessFileMinio(processFile, FileEventType.UPDATED);
        } else {
            LOGGER.info("Creating a new file {} available at {}.", fileType, startTime);
            ProcessFile processFile = new ProcessFile(objectKey, fileGroup, fileType, startTime, endTime, getTimestampNowWithProcessTimezone());
            return new ProcessFileMinio(processFile, FileEventType.AVAILABLE);
        }
    }

    private OffsetDateTime getTimestampNowWithProcessTimezone() {
        return OffsetDateTime.now(taskManagerConfigurationProperties.getProcessTimezone());
    }

    private void addWaitingFileAndNotifyTasks(ProcessFileMinio processFileMinio, Set<Task> tasks) {
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.PENDING) {
                removeWaitingFileWithSameTypeAndValidity(processFileMinio);
                waitingFilesList.add(processFileMinio);
                LOGGER.info("process file {} is added to waiting files list", processFileMinio.getProcessFile().getFilename());
                taskService.addFileEventToTask(task, FileEventType.WAITING, processFileMinio.getProcessFile(), "WARN");
                saveAndNotifyTasks(Collections.singleton(new TaskWithStatusUpdate(task, false))); //No need to update status when the file is waiting
            }
        }
    }

    private void removeWaitingFileWithSameTypeAndValidity(ProcessFileMinio newProcessFileMinio) {
        Set<ProcessFileMinio> processFileMiniosWaiting = waitingFilesList.stream().filter(processFileMinio -> processFileMinio.hasSameTypeAndValidity(newProcessFileMinio)).collect(Collectors.toSet());
        for (ProcessFileMinio processFileMinio : processFileMiniosWaiting) {
            waitingFilesList.remove(processFileMinio);
            LOGGER.info(PROCESS_FILE_REMOVED_MESSAGE, processFileMinio.getProcessFile().getFilename());
        }
    }

    public void emptyWaitingList(OffsetDateTime timestamp) {
        LOGGER.info("Handle Emptying of waiting list..");
        LOGGER.info("Waiting list contains {} files", waitingFilesList.size());
        List<ProcessFileMinio> waitingProcessFilesToAdd = getWaitingProcessFilesForTimestamp(timestamp);
        int processFilesSize = waitingProcessFilesToAdd.size();

        if (processFilesSize >= 1) {
            // each process file is added to the task, but the status is updated only for the last file waiting (withStatusUpdate = true parameter)
            for (int i = 0; i < processFilesSize - 1; i++) {
                ProcessFileMinio processFileMinio = waitingProcessFilesToAdd.get(i);
                ProcessFile processFile = processFileRepository.save(processFileMinio.getProcessFile());
                Set<TaskWithStatusUpdate> tasksWithStatusUpdate = taskService.addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), true, false);
                saveAndNotifyTasks(tasksWithStatusUpdate);
                waitingFilesList.remove(processFileMinio);
                LOGGER.info(PROCESS_FILE_REMOVED_MESSAGE, processFile.getFilename());
            }
            ProcessFileMinio lastProcessFileMinio = waitingProcessFilesToAdd.get(processFilesSize - 1);
            final ProcessFile lastProcessFile = processFileRepository.save(lastProcessFileMinio.getProcessFile());
            Set<TaskWithStatusUpdate> tasksWithStatusUpdate = taskService.addProcessFileToTasks(lastProcessFile, lastProcessFileMinio.getFileEventType(), true, true);
            saveAndNotifyTasks(tasksWithStatusUpdate);
            waitingFilesList.remove(lastProcessFileMinio);
            LOGGER.info(PROCESS_FILE_REMOVED_MESSAGE, lastProcessFile.getFilename());
        }
    }

    List<ProcessFileMinio> getWaitingProcessFilesForTimestamp(OffsetDateTime timestamp) {
        List<ProcessFileMinio> processFilesWithFinishedTasks = new ArrayList<>();
        for (ProcessFileMinio processFileMinio : waitingFilesList) {
            ProcessFile processFile = processFileMinio.getProcessFile();
            if (isFileValidForTimestamp(timestamp, processFile)) {
                Set<Task> taskForProcessFile = taskRepository.findAllByTimestampBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate());
                if (!isAnyTaskRunningOrPending(taskForProcessFile)) {
                    processFilesWithFinishedTasks.add(processFileMinio);
                    LOGGER.info("process file to add {} for timestamp {}", processFileMinio.getProcessFile().getFilename(), timestamp);
                }
            }
        }
        return processFilesWithFinishedTasks;
    }

    private boolean isFileValidForTimestamp(OffsetDateTime timestamp, ProcessFile processFile) {
        OffsetDateTime startingAvailabilityDate = processFile.getStartingAvailabilityDate();
        OffsetDateTime endingAvailabilityDate = processFile.getEndingAvailabilityDate();
        return (timestamp.equals(startingAvailabilityDate) || timestamp.isAfter(startingAvailabilityDate)) && timestamp.isBefore(endingAvailabilityDate);
    }

    @Transactional
    public void removeProcessFile(Event event) {
        synchronized (TASK_MANAGER_LOCK) {
            String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
            LOGGER.info("Removing MinIO object {}", objectKey);
            Optional<ProcessFile> optionalProcessFile = processFileRepository.findByFileObjectKey(objectKey);
            if (optionalProcessFile.isPresent()) {
                ProcessFile processFile = optionalProcessFile.get();
                LOGGER.debug("Finding tasks related to {}", processFile.getFilename());
                saveAndNotifyTasks(taskService.removeProcessFileFromTasks(processFile));
                processFileRepository.delete(processFile);
                LOGGER.info("Process file {} has been removed properly", processFile.getFilename());
            } else {
                LOGGER.info("File not referenced in the database. Nothing to do.");
            }
        }
    }

    private void saveAndNotifyTasks(Set<TaskWithStatusUpdate> taskWithStatusUpdateSet) {
        LOGGER.debug("Saving related tasks in DB");
        taskRepository.saveAllAndFlush(taskWithStatusUpdateSet.stream().map(TaskWithStatusUpdate::getTask).collect(Collectors.toList()));
        LOGGER.debug("Notifying on web-sockets");
        taskUpdateNotifier.notify(taskWithStatusUpdateSet);
    }
}
