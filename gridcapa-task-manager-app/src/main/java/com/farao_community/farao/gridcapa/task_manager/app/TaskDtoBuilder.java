/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessEventDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskDtoBuilder {

    private final TaskManagerConfigurationProperties properties;
    private final TaskRepository taskRepository;

    public TaskDtoBuilder(TaskManagerConfigurationProperties properties, TaskRepository taskRepository) {
        this.properties = properties;
        this.taskRepository = taskRepository;
    }

    public TaskDto getTaskDto(OffsetDateTime timestamp) {
        return taskRepository.findByTimestamp(timestamp)
            .map(this::createDtoFromEntity)
            .orElse(getEmptyTask(timestamp));
    }

    public TaskDto getEmptyTask(OffsetDateTime timestamp) {
        return TaskDto.emptyTask(timestamp, properties.getProcess().getInputs());
    }

    public TaskDto createDtoFromEntity(Task task) {
        return new TaskDto(
            task.getId(),
            task.getTimestamp(),
            task.getStatus(),
            properties.getProcess().getInputs().stream()
                .map(input -> task.getProcessFile(input)
                    .map(this::createDtoFromEntity)
                    .orElseGet(() -> ProcessFileDto.emptyProcessFile(input)))
                .collect(Collectors.toList()),
            task.getProcessEvents().stream().map(this::createDtoFromEntity).collect(Collectors.toList()));
    }

    public ProcessFileDto createDtoFromEntity(ProcessFile processFile) {
        return new ProcessFileDto(
            processFile.getFileType(),
            ProcessFileStatus.VALIDATED,
            processFile.getFilename(),
            processFile.getLastModificationDate(),
            processFile.getFileUrl());
    }

    public ProcessEventDto createDtoFromEntity(ProcessEvent processEvent) {
        LocalDateTime localDateTime = convertOffsetToLocalAtSameInstant(processEvent.getTimestamp());
        return new ProcessEventDto(localDateTime,
            processEvent.getLevel(),
            processEvent.getMessage());
    }

    private LocalDateTime convertOffsetToLocalAtSameInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toZonedDateTime().withZoneSameInstant(ZoneId.of(properties.getProcess().getTimezone())).toLocalDateTime();
    }
}
