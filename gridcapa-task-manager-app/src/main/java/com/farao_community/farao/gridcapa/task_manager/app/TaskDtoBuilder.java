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

import java.time.*;
import java.util.*;
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

    public List<TaskDto> getListTasksDto(LocalDate businessDate) {
        ZoneId zone = ZoneId.of(properties.getProcess().getTimezone());
        LocalDateTime businessDateStartTime = businessDate.atTime(0, 30);
        LocalDateTime businessDateEndTime = businessDate.atTime(23, 30);
        ZoneOffset zoneOffSet = zone.getRules().getOffset(businessDateStartTime);
        OffsetDateTime startTimestamp = businessDateStartTime.atOffset(zoneOffSet);
        OffsetDateTime endTimestamp = businessDateEndTime.atOffset(zoneOffSet);

        Set<Task> tasks = taskRepository.findAllByTimestampBetweenForBusinessDayView(startTimestamp, endTimestamp);
        Map<OffsetDateTime, TaskDto> taskMap = new HashMap<>();
        for (OffsetDateTime loopTimestamp = startTimestamp;
             !loopTimestamp.isAfter(endTimestamp);
             loopTimestamp = loopTimestamp.plusHours(1).atZoneSameInstant(zone).toOffsetDateTime()
        ) {
            OffsetDateTime taskTimeStamp = loopTimestamp.atZoneSameInstant(ZoneId.of("Z")).toOffsetDateTime();
            taskMap.put(taskTimeStamp, getEmptyTask(taskTimeStamp));
        }

        tasks.stream().map(t -> createDtoFromEntityWithOrWithoutEvents(t, false)).forEach(dto -> taskMap.put(dto.getTimestamp(), dto));

        return taskMap.values().stream().toList();
    }

    public List<TaskDto> getListRunningTasksDto() {
        return taskRepository.findAllRunningAndPending().stream().map(t -> createDtoFromEntityWithOrWithoutEvents(t, false)).toList();
    }

    public TaskDto getEmptyTask(OffsetDateTime timestamp) {
        return TaskDto.emptyTask(timestamp, properties.getProcess().getInputs(), properties.getProcess().getOutputs());
    }

    public TaskDto createDtoFromEntity(Task task) {
        return createDtoFromEntityWithOrWithoutEvents(task, true);
    }

    private TaskDto createDtoFromEntityWithOrWithoutEvents(Task task, boolean withEvents) {
        var inputs = properties.getProcess().getInputs().stream()
                .map(input -> task.getInput(input)
                        .map(this::createDtoFromEntity)
                        .orElseGet(() -> ProcessFileDto.emptyProcessFile(input)))
                .collect(Collectors.toList());
        var optionalInputs = properties.getProcess().getOptionalInputs().stream()
                .map(input -> task.getInput(input)
                        .map(this::createDtoFromEntity)
                        .orElseGet(() -> ProcessFileDto.emptyProcessFile(input)))
                .toList();
        inputs.addAll(optionalInputs);
        var outputs = properties.getProcess().getOutputs().stream()
                .map(output -> task.getOutput(output)
                        .map(this::createDtoFromEntity)
                        .orElseGet(() -> ProcessFileDto.emptyProcessFile(output)))
                .toList();

        if (withEvents) {
            return new TaskDto(
                    task.getId(),
                    task.getTimestamp(),
                    task.getStatus(),
                    inputs,
                    outputs,
                    task.getProcessEvents().stream().map(this::createDtoFromEntity).toList());
        } else {
            return new TaskDto(
                    task.getId(),
                    task.getTimestamp(),
                    task.getStatus(),
                    inputs,
                    outputs,
                    Collections.emptyList());
        }
    }

    public ProcessFileDto createDtoFromEntity(ProcessFile processFile) {
        return new ProcessFileDto(
            processFile.getFileObjectKey(),
            processFile.getFileType(),
            ProcessFileStatus.VALIDATED,
            processFile.getFilename(),
            processFile.getLastModificationDate());
    }

    public ProcessEventDto createDtoFromEntity(ProcessEvent processEvent) {
        return new ProcessEventDto(processEvent.getTimestamp(),
            processEvent.getLevel(),
            processEvent.getMessage(),
            processEvent.getServiceName());
    }
}
