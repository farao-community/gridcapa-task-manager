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
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    private final MinioAdapter minioAdapter;

    public TaskManager(TaskUpdateNotifier taskUpdateNotifier,
                       TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                       TaskRepository taskRepository,
                       MinioAdapter minioAdapter) {
        this.taskUpdateNotifier = taskUpdateNotifier;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskRepository = taskRepository;
        this.minioAdapter = minioAdapter;
    }

    @Bean
    public Consumer<TaskStatusUpdate> handleTaskStatusUpdate() {
        return taskStatusUpdate -> {
            Optional<Task> optionalTask = taskRepository.findById(taskStatusUpdate.getId());
            if (optionalTask.isPresent()) {
                Task task = optionalTask.get();
                task.setStatus(taskStatusUpdate.getTaskStatus());
                taskRepository.save(task);
                taskUpdateNotifier.notify(task);
            } else {
                LOGGER.warn("Task {} does not exist. Impossible to update status", taskStatusUpdate.getId());
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
                    ProcessEvent processEvent = new ProcessEvent(task, LocalDateTime.parse(loggerEvent.getTimestamp().substring(0, 19)), loggerEvent.getLevel(), loggerEvent.getMessage());
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
        List<Task> tasks = new ArrayList<>();
        TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
        if (processProperties.getTag().equals(event.userMetadata().get(FILE_PROCESS_TAG))
                && processProperties.getInputs().contains(event.userMetadata().get(FILE_TYPE))) {
            String fileType = event.userMetadata().get(FILE_TYPE);
            String validityInterval = event.userMetadata().get(FILE_VALIDITY_INTERVAL);
            if (validityInterval != null) {
                String[] interval = validityInterval.split("/");
                LocalDateTime currentTime = toUtc(interval[0], processProperties.getTimezone());
                LocalDateTime endTime = toUtc(interval[1], processProperties.getTimezone());
                while (currentTime.isBefore(endTime)) {
                    final LocalDateTime finalTime = currentTime;
                    Task task = taskRepository.findByTimestamp(finalTime).orElseGet(() -> {
                        LOGGER.info("New task added for {} on {}", processProperties.getTag(), finalTime);
                        return new Task(finalTime, processProperties.getInputs());
                    });
                    String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
                    addFileToTask(task, fileType, objectKey, minioAdapter.generatePreSignedUrl(event));
                    tasks.add(task);
                    currentTime = currentTime.plusHours(1);
                }
                taskRepository.saveAll(tasks);
                taskUpdateNotifier.notify(tasks);
            }
        }
    }

    public void removeProcessFile(Event event) {
        List<Task> tasksToBeDeleted = new ArrayList<>();
        String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
        List<Task> impactedTasks = taskRepository.findAllByProcessFilesFileObjectKey(objectKey);
        impactedTasks.parallelStream().forEach(task -> {
            task.getProcessFiles().forEach(processFile -> {
                if (objectKey.equals(processFile.getFileObjectKey())) {
                    addFileEventToTask(task, FileEventType.DELETED, processFile.getFileType(), processFile.getFilename());
                    processFile.setProcessFileStatus(ProcessFileStatus.DELETED);
                    processFile.setFileObjectKey(null);
                    processFile.setFileUrl(null);
                    processFile.setFilename(null);
                    processFile.setLastModificationDate(getProcessNow());
                }
            });
            if (task.getProcessFiles().stream().allMatch(this::isProcessFileReadyForTaskDeletion)) {
                tasksToBeDeleted.add(task);
            }

        });
        List<Task> tasksToBeKept = impactedTasks.stream().filter(task -> !tasksToBeDeleted.contains(task)).collect(Collectors.toList());
        taskRepository.saveAll(tasksToBeKept);
        taskRepository.deleteAll(tasksToBeDeleted);
        taskUpdateNotifier.notify(impactedTasks);
    }

    private static LocalDateTime toUtc(String timestamp, String fromZoneId) {
        return LocalDateTime.parse(timestamp).atZone(ZoneId.of(fromZoneId)).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }

    private boolean isProcessFileReadyForTaskDeletion(ProcessFile processFile) {
        switch (processFile.getProcessFileStatus()) {
            case DELETED:
            case NOT_PRESENT:
                return true;
            default:
                return false;
        }
    }

    private LocalDateTime getProcessNow() {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
        return LocalDateTime.now(ZoneId.of(processProperties.getTimezone()));
    }

    private void addFileToTask(Task task, String fileType, String objectKey, String fileUrl) {
        LOGGER.info("New file added to task {} with file type {} at URL {}", task.getTimestamp(), fileType, fileUrl);
        ProcessFile processFile = task.getProcessFile(fileType);
        String fileName = FilenameUtils.getName(objectKey);
        if (processFile.getProcessFileStatus().equals(ProcessFileStatus.NOT_PRESENT)) {
            addFileEventToTask(task, FileEventType.AVAILABLE, fileType, fileName);
        } else {
            addFileEventToTask(task, FileEventType.UPDATED, fileType, fileName);
        }
        processFile.setFileUrl(fileUrl);
        processFile.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        processFile.setLastModificationDate(getProcessNow());
        processFile.setFileObjectKey(objectKey);
        processFile.setFilename(fileName);
        if (task.getProcessFiles().stream().map(ProcessFile::getProcessFileStatus).allMatch(processFileStatus -> processFileStatus.equals(ProcessFileStatus.VALIDATED))) {
            LOGGER.info("Task {} is ready to run", task.getTimestamp());
            task.setStatus(TaskStatus.READY);
        }
    }

    private void addFileEventToTask(Task task, FileEventType fileEventType, String fileType, String fileName) {
        String message = getFileEventMessage(fileEventType, fileType, fileName);
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

    public TaskDto getTaskDto(LocalDateTime timestamp) {
        return taskRepository.findByTimestamp(timestamp).map(Task::createDtoFromEntity).orElse(getEmptyTask(timestamp));
    }

    public TaskDto getEmptyTask(LocalDateTime timestamp) {
        return TaskDto.emptyTask(timestamp, taskManagerConfigurationProperties.getProcess().getInputs());
    }

    private enum FileEventType {
        AVAILABLE,
        UPDATED,
        DELETED
    }
}
