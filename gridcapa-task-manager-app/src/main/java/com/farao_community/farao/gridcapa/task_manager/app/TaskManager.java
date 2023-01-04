/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.*;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.api.TaskLogEventUpdate;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.messages.Event;
import io.minio.messages.NotificationRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);
    private static final Object LOCK = new Object();
    static final String FILE_GROUP_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_GROUP_METADATA_KEY;
    static final String FILE_TARGET_PROCESS_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TARGET_PROCESS_METADATA_KEY;
    static final String FILE_TYPE_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TYPE_METADATA_KEY;
    static final String FILE_VALIDITY_INTERVAL_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_VALIDITY_INTERVAL_METADATA_KEY;
    private static final String FILE_EVENT_DEFAULT_LEVEL = "INFO";

    private final TaskUpdateNotifier taskUpdateNotifier;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final ProcessFileRepository processFileRepository;
    private final MinioAdapter minioAdapter;

    public TaskManager(TaskUpdateNotifier taskUpdateNotifier,
                       TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                       TaskRepository taskRepository,
                       ProcessFileRepository processFileRepository,
                       MinioAdapter minioAdapter) {
        this.taskUpdateNotifier = taskUpdateNotifier;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskRepository = taskRepository;
        this.processFileRepository = processFileRepository;
        this.minioAdapter = minioAdapter;
    }

    @Bean
    public Consumer<Flux<TaskStatusUpdate>> consumeTaskStatusUpdate() {
        return f -> f.subscribe(taskStatusUpdate -> {
            try {
                handleTaskStatusUpdate(taskStatusUpdate);
            } catch (Exception e) {
                LOGGER.error(String.format("Unable to handle task status update properly %s", taskStatusUpdate), e);
            }
        });
    }

    public void handleTaskStatusUpdate(TaskStatusUpdate taskStatusUpdate) {
        synchronized (LOCK) {
            Optional<Task> optionalTask = taskRepository.findByIdWithProcessFiles(taskStatusUpdate.getId());
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatusUpdate.getTaskStatus());
            } else {
                LOGGER.warn("Task {} does not exist. Impossible to update status", taskStatusUpdate.getId());
            }
        }
    }

    public Optional<Task> handleTaskStatusUpdate(OffsetDateTime timestamp, TaskStatus taskStatus) {
        synchronized (LOCK) {
            Optional<Task> optionalTask = taskRepository.findByTimestamp(timestamp);
            if (optionalTask.isPresent()) {
                updateTaskStatus(optionalTask.get(), taskStatus);
                return optionalTask;
            } else {
                LOGGER.warn("Task at {} does not exist. Impossible to update status", timestamp);
                return Optional.empty();
            }
        }
    }

    private void updateTaskStatus(Task task, TaskStatus taskStatus) {
        task.setStatus(taskStatus);
        taskRepository.saveAndFlush(task);
        taskUpdateNotifier.notify(task, true);
        LOGGER.debug("Task status has been updated on {} to {}", task.getTimestamp(), taskStatus);
    }

    @Bean
    public Consumer<Flux<String>> consumeTaskEventUpdate() {
        return f -> f.subscribe(event -> {
            try {
                handleTaskEventUpdate(event);
            } catch (Exception e) {
                LOGGER.error(String.format("Unable to handle task event update properly %s", event), e);
            }
        });
    }

    void handleTaskEventUpdate(String loggerEventString) {
        synchronized (LOCK) {
            try {
                TaskLogEventUpdate loggerEvent = new ObjectMapper().readValue(loggerEventString, TaskLogEventUpdate.class);
                Optional<Task> optionalTask = taskRepository.findByIdWithProcessFiles(UUID.fromString(loggerEvent.getId()));
                if (optionalTask.isPresent()) {
                    Task task = optionalTask.get();
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(loggerEvent.getTimestamp());
                    String updatedMessage = loggerEvent.getEventPrefix().isPresent() ? "[" + loggerEvent.getEventPrefix().get() + "] : " + loggerEvent.getMessage() : loggerEvent.getMessage();
                    task.addProcessEvent(offsetDateTime, loggerEvent.getLevel(), updatedMessage);
                    taskRepository.save(task);
                    taskUpdateNotifier.notify(task, false);
                    LOGGER.debug("Task event has been added on {} provided by {}", task.getTimestamp(), loggerEvent.getServiceName());
                } else {
                    LOGGER.warn("Task {} does not exist. Impossible to update task with log event", loggerEvent.getId());
                }
            } catch (JsonProcessingException e) {
                LOGGER.warn("Couldn't parse log event, Impossible to match the event with concerned task", e);
            }
        }
    }

    @Bean
    public Consumer<Flux<NotificationRecords>> consumeMinioEvent() {
        return f -> f.subscribe(nr -> {
            try {
                handleMinioEvent(nr);
            } catch (Exception e) {
                LOGGER.error("Unable to handle MinIO event properly", e);
            }
        });
    }

    public void handleMinioEvent(NotificationRecords notificationRecords) {
        notificationRecords.events().forEach(event -> {
            LOGGER.debug("s3 event received");
            switch (event.eventType()) {
                case OBJECT_CREATED_ANY:
                case OBJECT_CREATED_PUT:
                case OBJECT_CREATED_POST:
                case OBJECT_CREATED_COPY:
                case OBJECT_CREATED_COMPLETE_MULTIPART_UPLOAD:
                    updateTasks(event);
                    break;
                case OBJECT_REMOVED_ANY:
                case OBJECT_REMOVED_DELETE:
                case OBJECT_REMOVED_DELETED_MARKER_CREATED:
                    removeProcessFile(event);
                    break;
                default:
                    LOGGER.info("S3 event type {} not handled by task manager", event.eventType());
                    break;
            }
        });
    }

    public void updateTasks(Event event) {
        synchronized (LOCK) {
            TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
            if (!event.userMetadata().isEmpty() && processProperties.getTag().equals(event.userMetadata().get(FILE_TARGET_PROCESS_METADATA_KEY))) {
                String fileGroup = event.userMetadata().get(FILE_GROUP_METADATA_KEY);
                String fileType = event.userMetadata().get(FILE_TYPE_METADATA_KEY);
                String validityInterval = event.userMetadata().get(FILE_VALIDITY_INTERVAL_METADATA_KEY);
                String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
                if (validityInterval != null && !validityInterval.isEmpty()) {
                    LOGGER.info("Adding MinIO object {}", objectKey);
                    String[] interval = validityInterval.split("/");
                    ProcessFileArrival processFileArrival = getProcessFileArrival(
                        OffsetDateTime.parse(interval[0]),
                        OffsetDateTime.parse(interval[1]),
                        objectKey,
                        fileType,
                        fileGroup);
                    final ProcessFile savedProcessFile = processFileRepository.save(processFileArrival.processFile);
                    boolean checkStatusChange = MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(fileGroup);
                    saveAndNotifyTasks(addProcessFileToTasks(savedProcessFile, processFileArrival.fileEventType, checkStatusChange));
                    LOGGER.info("Process file {} has been added properly", processFileArrival.processFile.getFilename());
                } else {
                    LOGGER.warn("Minio object {} has not been added ", objectKey);
                }
            }
        }
    }

    private ProcessFileArrival getProcessFileArrival(OffsetDateTime startTime, OffsetDateTime endTime, String objectKey, String fileType, String fileGroup) {
        LOGGER.debug("Start finding process file");
        /*
        This implies that only one file per type and group can exist. If another one is imported it would just
        replace the previous one.
        */
        Optional<ProcessFile> optProcessFile = processFileRepository.findByStartingAvailabilityDateAndFileTypeAndGroup(startTime, fileType, fileGroup);
        if (optProcessFile.isPresent()) {
            LOGGER.info("File {} available at {} is already referenced in the database. Updating process file data.", fileType, startTime);
            ProcessFile processFile = optProcessFile.get();
            processFile.setFileUrl(minioAdapter.generatePreSignedUrl(objectKey));
            processFile.setFileObjectKey(objectKey);
            processFile.setLastModificationDate(getProcessNow());
            return new ProcessFileArrival(processFile, FileEventType.UPDATED);
        } else {
            LOGGER.info("Creating a new file {} available at {}.", fileType, startTime);
            ProcessFile processFile = new ProcessFile(objectKey, fileGroup, fileType, startTime, endTime, minioAdapter.generatePreSignedUrl(objectKey), getProcessNow());
            return new ProcessFileArrival(processFile, FileEventType.AVAILABLE);
        }
    }

    public void removeProcessFile(Event event) {
        synchronized (LOCK) {
            String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
            LOGGER.info("Removing MinIO object {}", objectKey);
            Optional<ProcessFile> optionalProcessFile = processFileRepository.findByFileObjectKey(objectKey);
            if (optionalProcessFile.isPresent()) {
                ProcessFile processFile = optionalProcessFile.get();
                LOGGER.debug("Finding tasks related to {}", processFile.getFilename());
                saveAndNotifyTasks(removeProcessFileFromTasks(processFile));
                processFileRepository.delete(processFile);
                LOGGER.info("Process file {} has been removed properly", processFile.getFilename());
            } else {
                LOGGER.info("File not referenced in the database. Nothing to do.");
            }
        }
    }

    private OffsetDateTime getProcessNow() {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
        return OffsetDateTime.now(ZoneId.of(processProperties.getTimezone()));
    }

    private void addFileEventToTask(Task task, FileEventType fileEventType, ProcessFile processFile) {
        String message = getFileEventMessage(fileEventType, processFile.getFileType(), processFile.getFilename());
        task.addProcessEvent(getProcessNow(), FILE_EVENT_DEFAULT_LEVEL, message);
    }

    private String getFileEventMessage(FileEventType fileEventType, String fileType, String fileName) {
        if (!fileEventType.equals(FileEventType.UPDATED)) {
            return String.format("The %s : '%s' is %s", fileType, fileName, fileEventType.toString().toLowerCase());
        } else {
            return String.format("A new version of %s is available : '%s'", fileType, fileName);
        }
    }

    private Set<TaskWithStatusUpdate> addProcessFileToTasks(ProcessFile processFile, FileEventType fileEventType, boolean checkStatusChange) {
        LOGGER.debug("Adding process file to the related tasks");
        return Stream.iterate(processFile.getStartingAvailabilityDate(), time -> time.plusHours(1))
            .limit(ChronoUnit.HOURS.between(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate()))
            .parallel()
            .map(timestamp -> {
                TaskWithStatusUpdate taskWithStatusUpdate = taskRepository.findByTimestamp(timestamp)
                    .map(task -> new TaskWithStatusUpdate(task, false))
                    .orElseGet(() -> new TaskWithStatusUpdate(taskRepository.save(new Task(timestamp)), true));
                Task task = taskWithStatusUpdate.getTask();
                addFileEventToTask(task, fileEventType, processFile);
                task.addProcessFile(processFile);
                // If the task is created the status will be set as updated whatever the check gives as output
                // and in case the task was not created here, it will depend on the output of the check.
                boolean statusUpdateDueToFileArrival = false;
                if (checkStatusChange) {
                    statusUpdateDueToFileArrival = checkAndUpdateTaskStatus(task);
                }
                taskWithStatusUpdate.setStatusUpdated(statusUpdateDueToFileArrival || taskWithStatusUpdate.isStatusUpdated());
                return taskWithStatusUpdate;
            })
            .collect(Collectors.toSet());
    }

    private Set<TaskWithStatusUpdate> removeProcessFileFromTasks(ProcessFile processFile) {
        LOGGER.debug("Removing process file of the related tasks");
        return taskRepository.findAllByTimestampBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate())
            .parallelStream()
            .map(task -> {
                task.removeProcessFile(processFile);
                boolean statusUpdated = false;
                if (MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(processFile.getFileGroup())) {
                    statusUpdated = checkAndUpdateTaskStatus(task);
                }
                if (task.getProcessFiles().isEmpty()) {
                    task.getProcessEvents().clear();
                } else {
                    addFileEventToTask(task, FileEventType.DELETED, processFile);
                }
                return new TaskWithStatusUpdate(task, statusUpdated);
            })
            .collect(Collectors.toSet());
    }

    /**
     * We compare the size of inputs list from process files of the task and the size of inputs from configuration.
     * If its equal task is ready otherwise it is created. When it is null it is not created.
     * This works because we consider there are only one file type per inputs. We call this method at adding and
     * deletion to check if the status has changed.
     *
     * @param task: Task on which to evaluate the status.
     */
    private boolean checkAndUpdateTaskStatus(Task task) {
        TaskStatus initialTaskStatus = task.getStatus();
        List<String> availableInputFileTypes = task.getProcessFiles().stream()
            .filter(processFile -> MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(processFile.getFileGroup()))
            .map(ProcessFile::getFileType).collect(Collectors.toList());
        if (availableInputFileTypes.isEmpty()) {
            task.setStatus(TaskStatus.NOT_CREATED);
        } else if (availableInputFileTypes.containsAll(taskManagerConfigurationProperties.getProcess().getInputs())) {
            task.setStatus(TaskStatus.READY);
        } else {
            task.setStatus(TaskStatus.CREATED);
        }
        return initialTaskStatus != task.getStatus();
    }

    private void saveAndNotifyTasks(Set<TaskWithStatusUpdate> taskWithStatusUpdateSet) {
        LOGGER.debug("Saving related tasks");
        taskRepository.saveAll(taskWithStatusUpdateSet.stream().map(TaskWithStatusUpdate::getTask).collect(Collectors.toSet()));
        LOGGER.debug("Notifying on web-sockets");
        taskUpdateNotifier.notify(taskWithStatusUpdateSet);
    }

    private static final class ProcessFileArrival {
        private final ProcessFile processFile;
        private final FileEventType fileEventType;

        private ProcessFileArrival(ProcessFile processFile, FileEventType fileEventType) {
            this.processFile = processFile;
            this.fileEventType = fileEventType;
        }
    }

    private enum FileEventType {
        AVAILABLE,
        UPDATED,
        DELETED
    }
}
