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

import java.time.LocalDateTime;
import java.time.ZoneId;

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

    public void updateTasks(Event event) {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
        if (processProperties.getTag().equals(event.userMetadata().get(FILE_PROCESS_TAG))
                && processProperties.getInputs().contains(event.userMetadata().get(FILE_TYPE))) {
            String fileType = event.userMetadata().get(FILE_TYPE);
            String validityInterval = event.userMetadata().get(FILE_VALIDITY_INTERVAL);
            if (validityInterval != null) {
                String[] interval = validityInterval.split("/");
                LocalDateTime currentTime = LocalDateTime.parse(interval[0])
                        .atZone(ZoneId.of(processProperties.getTimezone()))
                        .withZoneSameInstant(ZoneId.of("UTC"))
                        .toLocalDateTime();
                LocalDateTime endTime = LocalDateTime.parse(interval[1])
                        .atZone(ZoneId.of(processProperties.getTimezone()))
                        .withZoneSameInstant(ZoneId.of("UTC"))
                        .toLocalDateTime();
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

    private static void addFileToTask(Task task, String fileType, String fileUrl) {
        LOGGER.info("New file added to task {} with file type {} at URL {}", task.getTimestamp(), fileType, fileUrl);
        ProcessFile processFile = task.getProcessFile(fileType);
        processFile.setFileUrl(fileUrl);
        processFile.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        if (task.getProcessFiles().stream().map(ProcessFile::getProcessFileStatus).noneMatch(processFileStatus -> processFileStatus.equals(ProcessFileStatus.NOT_PRESENT))) {
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
