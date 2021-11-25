/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.farao.gridcapa_task_manager_app;

import com.rte_france.farao.gridcapa_task_manager_api.entities.TaskDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Controller
@RequestMapping
public class TaskManagerController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManagerController.class);

    @Autowired
    private final TaskManager taskManager;

    public TaskManagerController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @GetMapping(value = "/tasks/{timestamp}")
    public ResponseEntity<TaskDto> getTaskFromTimestamp(@PathVariable String timestamp) {
        return ResponseEntity.ok().body(taskManager.getTaskDto(LocalDateTime.parse(timestamp)));
    }

    @PostMapping(value = "/tasks/launch/{timestamp}")
    public ResponseEntity<TaskDto> launchTaskFromTimestamp(@PathVariable String timestamp) {
        LOGGER.info("Launching task {}", timestamp);
        taskManager.changeTask(LocalDateTime.parse(timestamp));
        return ResponseEntity.ok().body(taskManager.getTaskDto(LocalDateTime.parse(timestamp)));
    }
}
