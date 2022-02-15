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
    static final String FILE_PROCESS_TAG = "X-Amz-Meta-Gridcapa_file_target_process";
    static final String FILE_TYPE = "X-Amz-Meta-Gridcapa_file_type";
    static final String FILE_VALIDITY_INTERVAL = "X-Amz-Meta-Gridcapa_file_validity_interval";
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
        return f -> f
            .onErrorContinue((t, r) -> LOGGER.error(t.getMessage(), t))
            .subscribe(this::handleTaskStatusUpdate);
    }

    public void handleTaskStatusUpdate(TaskStatusUpdate taskStatusUpdate) {
        Optional<Task> optionalTask = taskRepository.findByIdWithProcessFiles(taskStatusUpdate.getId());
        if (optionalTask.isPresent()) {
            Task task = optionalTask.get();
            task.setStatus(taskStatusUpdate.getTaskStatus());
            taskRepository.save(task);
            taskUpdateNotifier.notify(task);
        } else {
            LOGGER.warn("Task {} does not exist. Impossible to update status", taskStatusUpdate.getId());
        }
    }

    @Bean
    public Consumer<Flux<String>> consumeTaskEventUpdate() {
        return f -> f
            .onErrorContinue((t, r) -> LOGGER.error(t.getMessage(), t))
            .subscribe(this::handleTaskEventUpdate);
    }

    void handleTaskEventUpdate(String loggerEventString) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            TaskLogEventUpdate loggerEvent = objectMapper.readValue(loggerEventString, TaskLogEventUpdate.class);
            Optional<Task> optionalTask = taskRepository.findByIdWithProcessFiles(UUID.fromString(loggerEvent.getId()));
            if (optionalTask.isPresent()) {
                Task task = optionalTask.get();
                OffsetDateTime offsetDateTime = OffsetDateTime.parse(loggerEvent.getTimestamp());
                task.addProcessEvent(offsetDateTime, loggerEvent.getLevel(), loggerEvent.getMessage());
                taskRepository.save(task);
                taskUpdateNotifier.notify(task);

            } else {
                LOGGER.warn("Task {} does not exist. Impossible to update task with log event", loggerEvent.getId());
            }
        } catch (JsonProcessingException e) {
            LOGGER.warn("Couldn't parse log event, Impossible to match the event with concerned task", e);
        }
    }

    @Bean
    public Consumer<Flux<NotificationRecords>> consumeMinioEvent() {
        return f -> f
            .onErrorContinue((t, r) -> LOGGER.error(t.getMessage(), t))
            .subscribe(this::handleMinioEvent);
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
        TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
        if (!event.userMetadata().isEmpty() && processProperties.getTag().equals(event.userMetadata().get(FILE_PROCESS_TAG))
                && processProperties.getInputs().contains(event.userMetadata().get(FILE_TYPE))) {
            String fileType = event.userMetadata().get(FILE_TYPE);
            String validityInterval = event.userMetadata().get(FILE_VALIDITY_INTERVAL);
            String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
            if (validityInterval != null && !validityInterval.isEmpty()) {
                LOGGER.info("Adding MinIO object {}", objectKey);
                String[] interval = validityInterval.split("/");
                ProcessFileArrival processFileArrival = getProcessFileArrival(
                    OffsetDateTime.parse(interval[0]),
                    OffsetDateTime.parse(interval[1]),
                    event,
                    objectKey,
                    fileType);
                processFileRepository.save(processFileArrival.processFile);
                saveAndNotifyTasks(addProcessFileToTasks(processFileArrival.processFile, processFileArrival.fileEventType));
                LOGGER.info("Process file {} has been added properly", processFileArrival.processFile.getFilename());
            } else {
                LOGGER.warn("Minio object {} has not been added ", objectKey);
            }
        }
    }

    private ProcessFileArrival getProcessFileArrival(OffsetDateTime startTime, OffsetDateTime endTime, Event event, String objectKey, String fileType) {
        LOGGER.debug("Start finding process file");
        Optional<ProcessFile> optProcessFile = processFileRepository.findByStartingAvailabilityDateAndFileType(startTime, fileType);
        if (optProcessFile.isPresent()) {
            LOGGER.info("File {} available at {} is already referenced in the database. Updating process file data.", fileType, startTime);
            ProcessFile processFile = optProcessFile.get();
            processFile.setFileUrl(minioAdapter.generatePreSignedUrl(event));
            processFile.setFileObjectKey(objectKey);
            processFile.setLastModificationDate(getProcessNow());
            return new ProcessFileArrival(processFile, FileEventType.UPDATED);
        } else {
            LOGGER.info("Creating a new file {} available at {}.", fileType, startTime);
            ProcessFile processFile = new ProcessFile(objectKey, fileType, startTime, endTime, minioAdapter.generatePreSignedUrl(event), getProcessNow());
            return new ProcessFileArrival(processFile, FileEventType.AVAILABLE);
        }
    }

    public void removeProcessFile(Event event) {
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

    private Set<Task> addProcessFileToTasks(ProcessFile processFile, FileEventType fileEventType) {
        LOGGER.debug("Adding process file to the related tasks");
        return Stream.iterate(processFile.getStartingAvailabilityDate(), time -> time.plusHours(1))
            .limit(ChronoUnit.HOURS.between(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate()))
            .parallel()
            .map(timestamp -> {
                Task task = taskRepository.findByTimestamp(timestamp).orElseGet(() -> new Task(timestamp));
                addFileEventToTask(task, fileEventType, processFile);
                task.addProcessFile(processFile);
                checkAndUpdateTaskStatus(task);
                return task;
            })
            .collect(Collectors.toSet());
    }

    private Set<Task> removeProcessFileFromTasks(ProcessFile processFile) {
        LOGGER.debug("Removing process file of the related tasks");
        return taskRepository.findAllByTimestampBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate())
            .parallelStream()
            .map(task -> {
                task.removeProcessFile(processFile);
                checkAndUpdateTaskStatus(task);
                if (task.getProcessFiles().isEmpty()) {
                    task.getProcessEvents().clear();
                } else {
                    addFileEventToTask(task, FileEventType.DELETED, processFile);
                }
                return task;
            })
            .collect(Collectors.toSet());
    }

    private void checkAndUpdateTaskStatus(Task task) {
        int fileNumber = task.getProcessFiles().size();
        if (fileNumber == 0) {
            task.setStatus(TaskStatus.NOT_CREATED);
        } else if (taskManagerConfigurationProperties.getProcess().getInputs().size() == fileNumber) {
            task.setStatus(TaskStatus.READY);
        } else {
            task.setStatus(TaskStatus.CREATED);
        }
    }

    private void saveAndNotifyTasks(Set<Task> tasks) {
        LOGGER.debug("Saving related tasks");
        taskRepository.saveAll(tasks);
        LOGGER.debug("Notifying on web-sockets");
        taskUpdateNotifier.notify(tasks);
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
