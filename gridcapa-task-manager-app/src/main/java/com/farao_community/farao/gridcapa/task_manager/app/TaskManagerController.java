/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Controller
@RequestMapping
@CrossOrigin(origins = "*")
public class TaskManagerController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManagerController.class);

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
        taskManager.runTask(LocalDateTime.parse(timestamp));
        return ResponseEntity.ok().body(taskManager.getTaskDto(LocalDateTime.parse(timestamp)));
    }
}
