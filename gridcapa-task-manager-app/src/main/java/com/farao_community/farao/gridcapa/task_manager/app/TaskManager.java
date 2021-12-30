/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.*;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

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
                       ProcessFileRepository processFileRepository, MinioAdapter minioAdapter) {
        this.taskUpdateNotifier = taskUpdateNotifier;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskRepository = taskRepository;
        this.processFileRepository = processFileRepository;
        this.minioAdapter = minioAdapter;
    }

    @Bean
    public Consumer<TaskStatusUpdate> handleTaskStatusUpdate() {
        return taskStatusUpdate -> {
            Optional<Task> optionalTask = taskRepository.findBySimpleNaturalId(taskStatusUpdate.getTimestamp());
            if (optionalTask.isPresent()) {
                Task task = optionalTask.get();
                task.setStatus(taskStatusUpdate.getTaskStatus());
                taskRepository.save(task);
                taskUpdateNotifier.notify(task);
            } else {
                LOGGER.warn("Task {} does not exist. Impossible to update status", taskStatusUpdate.getTimestamp());
            }
        };
    }

    @Bean
    public Consumer<String> handleTaskLogEventUpdate() {
        return loggerEventString -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                TaskLogEventUpdate loggerEvent = objectMapper.readValue(loggerEventString, TaskLogEventUpdate.class);
                Optional<Task> optionalTask = taskRepository.findById(UUID.fromString(loggerEvent.getId()));
                if (optionalTask.isPresent()) {
                    Task task = optionalTask.get();
                    LOGGER.info(loggerEvent.getTimestamp());
                    OffsetDateTime offsetDateTime = getOffsetDateTimeAtSameInstant(LocalDateTime.parse(loggerEvent.getTimestamp().substring(0, 19)));
                    ProcessEvent processEvent = new ProcessEvent(task, offsetDateTime, loggerEvent.getLevel(), loggerEvent.getMessage());
                    task.getProcessEvents().add(processEvent);
                    taskRepository.save(task);
                    taskUpdateNotifier.notify(task);
                } else {
                    LOGGER.warn("Task {} does not exist. Impossible to update task with log event", loggerEvent.getId());
                }
            } catch (JsonProcessingException e) {
                LOGGER.warn("Couldn't parse log event, Impossible to match the event with concerned task", e);
            }
        };
    }

    @Bean
    public Consumer<NotificationRecords> handleMinioEvent() {
        return notificationRecords -> notificationRecords.events().forEach(event -> {
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
        if (processProperties.getTag().equals(event.userMetadata().get(FILE_PROCESS_TAG))
                && processProperties.getInputs().contains(event.userMetadata().get(FILE_TYPE))) {
            String fileType = event.userMetadata().get(FILE_TYPE);
            String validityInterval = event.userMetadata().get(FILE_VALIDITY_INTERVAL);
            if (validityInterval != null) {
                String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
                String[] interval = validityInterval.split("/");
                OffsetDateTime currentTime = OffsetDateTime.parse(interval[0]);
                OffsetDateTime endTime = OffsetDateTime.parse(interval[1]);
                LOGGER.info("Start finding process file");
                Optional<ProcessFile> optProcessFile = processFileRepository.findProcessFileByStartingAvailabilityDateAndAndFileType(currentTime, fileType);
                ProcessFile processFile;
                FileEventType fileEventType;
                if (optProcessFile.isPresent()) {
                    LOGGER.info("Start updating process file");
                    processFile = optProcessFile.get();
                    processFile.setFileUrl(minioAdapter.generatePreSignedUrl(event));
                    processFile.setFileObjectKey(objectKey);
                    processFile.setLastModificationDate(getProcessNow());
                    fileEventType = FileEventType.UPDATED;
                } else {
                    LOGGER.info("Start creating process file");
                    processFile = new ProcessFile(objectKey, fileType, currentTime, endTime, minioAdapter.generatePreSignedUrl(event), getProcessNow());
                    fileEventType = FileEventType.AVAILABLE;
                }
                LOGGER.info("Start saving process file");
                processFileRepository.save(processFile);

                Set<Task> tasks = new HashSet<>();
                LOGGER.info("Starting tasks modification loop");
                while (currentTime.isBefore(endTime)) {
                    final OffsetDateTime finalTime = currentTime;
                    Task task = taskRepository.findTaskByTimestampEagerLeft(finalTime).orElseGet(() -> new Task(finalTime));
                    addFileEventToTask(task, fileEventType, processFile);
                    task.addProcessFile(processFile);
                    checkAndUpdateTaskReadiness(task);
                    tasks.add(task);
                    currentTime = currentTime.plusHours(1);
                }
                LOGGER.info("Start saving tasks");
                taskRepository.saveAll(tasks);
                LOGGER.info("Start notifying");
                taskUpdateNotifier.notify(tasks);
                LOGGER.info("End notifying");
            }
        }
    }

    public void removeProcessFile(Event event) {
        String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
        LOGGER.info("Finding process file");
        Optional<ProcessFile> optionalProcessFile = processFileRepository.findBySimpleNaturalId(objectKey);
        if (optionalProcessFile.isPresent()) {
            ProcessFile processFile = optionalProcessFile.get();
            Set<Task> tasks = new HashSet<>(processFile.getTasks());
            Set<Task> tasksToBeDeleted = new HashSet<>();
            for (Task task : tasks) {
                addFileEventToTask(task, FileEventType.DELETED, processFile);
                task.removeProcessFile(processFile);
                if (task.getProcessFiles().isEmpty()) {
                    task.getProcessEvents().clear();
                }
            }
            processFileRepository.delete(processFile);
            taskRepository.deleteAll(tasksToBeDeleted);
        }
    }

    private OffsetDateTime getProcessNow() {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
        return OffsetDateTime.now(ZoneId.of(processProperties.getTimezone()));
    }

    private void checkAndUpdateTaskReadiness(Task task) {
        if (taskManagerConfigurationProperties.getProcess().getInputs().size() == task.getProcessFilesNumber()) {
            task.setStatus(TaskStatus.READY);
        }
    }

    private void addFileEventToTask(Task task, FileEventType fileEventType, ProcessFile processFile) {
        String message = getFileEventMessage(fileEventType, processFile.getFileType(), processFile.getFilename());
        ProcessEvent event = new ProcessEvent(task, getProcessNow(), FILE_EVENT_DEFAULT_LEVEL, message);
        task.getProcessEvents().add(event);
    }

    private String getFileEventMessage(FileEventType fileEventType, String fileType, String fileName) {
        if (!fileEventType.equals(FileEventType.UPDATED)) {
            return String.format("The %s : '%s' is %s", fileType, fileName, fileEventType.toString().toLowerCase());
        } else {
            return String.format("A new version of %s is available : '%s'", fileType, fileName);
        }
    }

    public TaskDto getTaskDto(OffsetDateTime timestamp) {
        return taskRepository.findTaskByTimestampEager(timestamp).map(task -> Task.createDtoFromEntity(task, taskManagerConfigurationProperties.getProcess().getInputs())).orElse(getEmptyTask(timestamp));
    }

    public TaskDto getEmptyTask(OffsetDateTime timestamp) {
        return TaskDto.emptyTask(timestamp, taskManagerConfigurationProperties.getProcess().getInputs());
    }

    private enum FileEventType {
        AVAILABLE,
        UPDATED,
        DELETED
    }

    private OffsetDateTime getOffsetDateTimeAtSameInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.of(taskManagerConfigurationProperties.getProcess().getTimezone())).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }
}
