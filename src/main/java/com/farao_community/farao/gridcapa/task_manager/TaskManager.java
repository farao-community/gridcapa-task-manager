/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager;

import io.minio.messages.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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

    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final MinioAdapter minioAdapter;

    public TaskManager(TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                       TaskRepository taskRepository,
                       MinioAdapter minioAdapter) {
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
                OffsetDateTime currentTime = LocalDateTime.parse(interval[0]).atZone(ZoneId.of(processProperties.getTimezone())).toOffsetDateTime();
                OffsetDateTime endTime = LocalDateTime.parse(interval[1]).atZone(ZoneId.of(processProperties.getTimezone())).toOffsetDateTime();
                while (currentTime.isBefore(endTime)) {
                    final OffsetDateTime finalTime = currentTime;
                    Task task = taskRepository.findById(finalTime.toString()).orElseGet(() -> {
                        LOGGER.info("New task added for {} on {} with file type {} at URL {}",
                                processProperties.getTag(), finalTime, fileType, minioAdapter.generatePreSignedUrl(event));
                        return new Task(finalTime.toString(), processProperties.getInputs());
                    });
                    task.getFileTypeToUrlMap().put(fileType, minioAdapter.generatePreSignedUrl(event));
                    taskRepository.save(task);
                    currentTime = currentTime.plusHours(1);
                }
            }
        } else {
            LOGGER.info("No metadata nothing to do");
        }
    }
}
