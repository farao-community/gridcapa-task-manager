/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.entities.TaskWithStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

import static com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties.TASK_MANAGER_LOCK;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Daniel Thirion {@literal <daniel.thirion at rte-france.com>}
 */
@Service
public class FileSelectorService {

    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final TaskUpdateNotifier taskUpdateNotifier;

    public FileSelectorService(TaskRepository taskRepository,
                               TaskService taskService,
                               TaskUpdateNotifier taskUpdateNotifier) {
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.taskUpdateNotifier = taskUpdateNotifier;
    }

    public void selectFile(final OffsetDateTime timestamp, final String filetype, final String filename) {
        synchronized (TASK_MANAGER_LOCK) {
            TaskWithStatusUpdate taskWithStatusUpdate = taskService.selectFile(timestamp, filetype, filename);
            Task task = taskRepository.save(taskWithStatusUpdate.getTask());
            taskUpdateNotifier.notify(task, taskWithStatusUpdate.isStatusUpdated(), false);
        }
    }
}
