/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Daniel Thirion {@literal <daniel.thirion at rte-france.com>}
 */
@Service
public class FileSelectorService {

    private final TaskRepository taskRepository;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskUpdateNotifier taskUpdateNotifier;

    @Value("${spring.application.name}")
    private String serviceName;

    public FileSelectorService(TaskRepository taskRepository,
                               TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                               TaskUpdateNotifier taskUpdateNotifier) {
        this.taskRepository = taskRepository;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskUpdateNotifier = taskUpdateNotifier;
    }

    public void selectFile(final OffsetDateTime timestamp,
                           final String filetype,
                           final String filename) {
        synchronized (TaskManagerConfigurationProperties.TASK_MANAGER_LOCK) {
            Task task = taskRepository.findByTimestamp(timestamp).orElseThrow(TaskNotFoundException::new);
            if (doesStatusBlockFileSelection(task.getStatus())) {
                throw new TaskManagerException("Status of task does not allow to change selected file");
            }

            final ProcessFile processFile = task.getAvailableInputs(filetype)
                    .stream()
                    .filter(pf -> filename.equals(pf.getFilename()))
                    .findAny()
                    .orElseThrow(ProcessFileNotFoundException::new);
            task.selectProcessFile(processFile);

            String message = String.format("Manual selection of another version of %s : %s", filetype, filename);
            OffsetDateTime now = OffsetDateTime.now(taskManagerConfigurationProperties.getProcessTimezone());
            task.addProcessEvent(now, "INFO", message, serviceName);

            boolean doesStatusNeedReset = doesStatusNeedReset(task.getStatus());
            if (doesStatusNeedReset) {
                task.setStatus(TaskStatus.READY);
            }
            task = taskRepository.save(task);
            taskUpdateNotifier.notify(task, doesStatusNeedReset, false);
        }
    }

    private boolean doesStatusNeedReset(final TaskStatus status) {
        return TaskStatus.SUCCESS == status || TaskStatus.ERROR == status || TaskStatus.INTERRUPTED == status;
    }

    private boolean doesStatusBlockFileSelection(final TaskStatus status) {
        return TaskStatus.RUNNING == status || TaskStatus.PENDING == status || TaskStatus.STOPPING == status;
    }
}
