/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager;

import com.farao_community.farao.gridcapa.task_manager.entities.*;
import io.minio.messages.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);
    static final String FILE_PROCESS_TAG = "X-Amz-Meta-Gridcapa_file_target_process";
    static final String FILE_TYPE = "X-Amz-Meta-Gridcapa_file_type";
    static final String FILE_VALIDITY_INTERVAL = "X-Amz-Meta-Gridcapa_file_validity_interval";

    private final TaskNotifier taskNotifier;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final MinioAdapter minioAdapter;

    public TaskManager(TaskNotifier taskNotifier,
                       TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                       TaskRepository taskRepository,
                       MinioAdapter minioAdapter) {
        this.taskNotifier = taskNotifier;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskRepository = taskRepository;
        this.minioAdapter = minioAdapter;
    }

    private static LocalDateTime toUtc(String timestamp, String fromZoneId) {
        return LocalDateTime.parse(timestamp).atZone(ZoneId.of(fromZoneId)).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }

    public void updateTasks(Event event) {
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
                    addFileToTask(task, fileType, minioAdapter.generatePreSignedUrl(event));
                    taskRepository.save(task);
                    taskNotifier.notifyUpdate(task);
                    currentTime = currentTime.plusHours(1);
                }
            }
        }
    }

    public void removeProcessFile(Event event) {
        String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
        List<Task> impactedTasks = taskRepository.findAllByProcessFilesFileObjectKey(objectKey);
        impactedTasks.forEach(task -> {
            task.getProcessFiles().forEach(processFile -> {
                if (objectKey.equals(processFile.getFileObjectKey())) {
                    processFile.setProcessFileStatus(ProcessFileStatus.DELETED);
                    processFile.setFileObjectKey(null);
                    processFile.setFileUrl(null);
                    processFile.setFilename(null);
                    processFile.setLastModificationDate(getProcessNow());
                }
            });
            taskRepository.save(task);
            if (task.getProcessFiles().stream().allMatch(this::isProcessFileReadyForTaskDeletion)) {
                taskRepository.delete(task);
            }
        });
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

    private void addFileToTask(Task task, String fileType, String fileUrl) {
        LOGGER.info("New file added to task {} with file type {} at URL {}", task.getTimestamp(), fileType, fileUrl);
        ProcessFile processFile = task.getProcessFile(fileType);
        processFile.setFileUrl(fileUrl);
        processFile.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        processFile.setLastModificationDate(getProcessNow());
        if (task.getProcessFiles().stream().map(ProcessFile::getProcessFileStatus).allMatch(processFileStatus -> processFileStatus.equals(ProcessFileStatus.VALIDATED))) {
            LOGGER.info("Task {} is ready to run", task.getTimestamp());
            task.setStatus(TaskStatus.READY);
        }
    }

    public TaskDto getTaskDto(LocalDateTime timestamp) {
        return taskRepository.findByTimestamp(timestamp).map(TaskDto::fromEntity).orElse(getEmptyTask(timestamp));
    }

    public TaskDto getEmptyTask(LocalDateTime timestamp) {
        return TaskDto.emptyTask(timestamp, taskManagerConfigurationProperties.getProcess().getInputs());
    }
}
