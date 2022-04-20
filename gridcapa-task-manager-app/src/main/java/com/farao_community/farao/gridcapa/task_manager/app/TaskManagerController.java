/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Controller
@RequestMapping
public class TaskManagerController {

    private final TaskDtoBuilder builder;
    private final TaskManager taskManager;

    public TaskManagerController(TaskDtoBuilder builder, TaskManager taskManager) {
        this.builder = builder;
        this.taskManager = taskManager;
    }

    @GetMapping(value = "/tasks/{timestamp}")
    public ResponseEntity<TaskDto> getTaskFromTimestamp(@PathVariable String timestamp) {
        return ResponseEntity.ok().body(builder.getTaskDto(OffsetDateTime.parse(timestamp)));
    }

    @PutMapping(value = "/tasks/{timestamp}/status")
    public ResponseEntity<TaskDto> updateStatus(@PathVariable String timestamp, @RequestParam String status) {
        TaskStatus taskStatus;
        try {
            taskStatus = TaskStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Task> optTask = taskManager.handleTaskStatusUpdate(OffsetDateTime.parse(timestamp), taskStatus);
        return optTask.map(task -> ResponseEntity.ok(builder.createDtoFromEntity(task)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/tasks/businessdate/{businessDate}")
    public ResponseEntity<List<TaskDto>> getListTasksFromBusinessDate(@PathVariable String businessDate) {
        return ResponseEntity.ok().body(builder.getListTasksDto(LocalDate.parse(businessDate)));
    }
}
