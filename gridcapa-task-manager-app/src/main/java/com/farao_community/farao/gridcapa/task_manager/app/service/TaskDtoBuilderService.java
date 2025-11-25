/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessEventDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessRunDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskParameterDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessRun;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskDtoBuilderService {

    private static final ZoneId UTC_ZONE = ZoneId.of("Z");
    private final TaskManagerConfigurationProperties properties;
    private final TaskRepository taskRepository;
    private final ZoneId localZone;
    private final ParameterService parameterService;

    public TaskDtoBuilderService(TaskManagerConfigurationProperties properties, TaskRepository taskRepository, ParameterService parameterService) {
        this.properties = properties;
        this.taskRepository = taskRepository;
        this.parameterService = parameterService;
        this.localZone = ZoneId.of(this.properties.getProcess().getTimezone());
    }

    /**
     * To use only if Process Events are required in the process. Otherwise, prefer the method getTaskDtoWithoutProcessEvents.
     * @param timestamp
     * @return
     */
    public TaskDto getTaskDtoWithProcessEvents(OffsetDateTime timestamp) {
        return taskRepository.findByTimestampAndFetchProcessEvents(timestamp)
                .map(this::createDtoFromEntity)
                .orElse(getEmptyTask(timestamp));
    }

    /**
     * To use preferably, as it does not fetch process event hence reducing memory used.
     * @param timestamp
     * @return
     */
    public TaskDto getTaskDtoWithoutProcessEvents(OffsetDateTime timestamp) {
        return taskRepository.findByTimestamp(timestamp)
                .map(this::createDtoFromEntityWithoutProcessEvents)
                .orElse(getEmptyTask(timestamp));
    }

    public List<TaskDto> getListTasksDto(final LocalDate businessDate) {
        final OffsetDateTime startTimestamp = getDateAtOffset(businessDate.atTime(0, 30));
        final OffsetDateTime endTimestamp = getDateAtOffset(businessDate.atTime(23, 30));

        Set<Task> tasks = taskRepository.findAllByTimestampBetweenForBusinessDayView(startTimestamp, endTimestamp);
        Map<OffsetDateTime, TaskDto> taskMap = new HashMap<>();
        for (OffsetDateTime loopTimestamp = startTimestamp;
             !loopTimestamp.isAfter(endTimestamp);
             loopTimestamp = loopTimestamp.plusHours(1).atZoneSameInstant(localZone).toOffsetDateTime()
        ) {
            OffsetDateTime taskTimeStamp = loopTimestamp.atZoneSameInstant(UTC_ZONE).toOffsetDateTime();
            taskMap.put(taskTimeStamp, getEmptyTask(taskTimeStamp));
        }

        tasks.stream()
                .map(this::createDtoFromEntityWithoutProcessEvents)
                .forEach(dto -> taskMap.put(dto.getTimestamp(), dto));

        return taskMap.values().stream().toList();
    }

    public boolean areAllTasksOverForBusinessDate(final LocalDate businessDate) {
        final OffsetDateTime startTimestamp = getDateAtOffset(businessDate.atTime(0, 0));
        final OffsetDateTime endTimestamp = getDateAtOffset(businessDate.atTime(23, 59));
        final List<TaskStatus> statuses = taskRepository.findTaskStatusByTimestampBetween(startTimestamp, endTimestamp);
        return statuses.stream().allMatch(TaskStatus::isOver);
    }

    public List<TaskDto> getListRunningTasksDto() {
        return taskRepository.findAllRunningAndPending().stream()
                .map(this::createDtoFromEntityWithoutProcessEvents)
                .toList();
    }

    public TaskDto getEmptyTask(OffsetDateTime timestamp) {
        return TaskDto.emptyTask(
                timestamp,
                properties.getProcess().getInputs(),
                properties.getProcess().getOutputs());
    }

    public TaskDto createDtoFromEntity(Task task) {
        return createDtoFromEntityWithOrWithoutEvents(task, true);
    }

    public TaskDto createDtoFromEntityWithoutProcessEvents(Task task) {
        return createDtoFromEntityWithOrWithoutEvents(task, false);
    }

    private OffsetDateTime getDateAtOffset(final LocalDateTime localDateTime) {
        final ZoneOffset zoneOffset = localZone.getRules().getOffset(localDateTime);
        return localDateTime.atOffset(zoneOffset);
    }

    private TaskDto createDtoFromEntityWithOrWithoutEvents(Task task, boolean withEvents) {
        List<ProcessFileDto> inputs = properties.getProcess().getInputs().stream()
                .map(input -> task.getInput(input)
                        .map(this::createDtoFromEntity)
                        .orElseGet(() -> ProcessFileDto.emptyProcessFile(input)))
                .collect(Collectors.toList());

        List<ProcessFileDto> availableInputs = properties.getProcess().getInputs()
                .stream()
                .flatMap(availableInput -> task.getAvailableInputs(availableInput)
                        .stream()
                        .map(this::createDtoFromEntity))
                .collect(Collectors.toList());

        List<ProcessFileDto> optionalInputs = properties.getProcess().getOptionalInputs().stream()
                .map(input -> task.getInput(input)
                        .map(this::createDtoFromEntity)
                        .orElseGet(() -> ProcessFileDto.emptyProcessFile(input)))
                .toList();
        inputs.addAll(optionalInputs);

        List<ProcessFileDto> availableOptionalInputs = properties.getProcess().getOptionalInputs().stream()
                .flatMap(input -> task.getInput(input)
                        .stream()
                        .map(this::createDtoFromEntity))
                .toList();
        availableInputs.addAll(availableOptionalInputs);

        List<ProcessFileDto> outputs = properties.getProcess().getOutputs().stream()
                .map(output -> task.getOutput(output)
                        .map(this::createDtoFromEntity)
                        .orElseGet(() -> ProcessFileDto.emptyProcessFile(output)))
                .toList();

        List<ProcessEventDto> processEvents = withEvents ?
                task.getProcessEvents().stream().map(this::createDtoFromEntity).toList()
                : Collections.emptyList();

        List<ProcessRunDto> runHistory = task.getRunHistory().stream()
                .map(this::createDtoFromEntity)
                .toList();

        List<TaskParameterDto> taskParameterDtos = parameterService.getParameters().stream()
                .map(TaskParameterDto::new)
                .toList();

        return new TaskDto(
                task.getId(),
                task.getTimestamp(),
                task.getStatus(),
                inputs,
                availableInputs,
                outputs,
                processEvents,
                runHistory,
                taskParameterDtos);
    }

    ProcessFileDto createDtoFromEntity(ProcessFile processFile) {
        return new ProcessFileDto(
                processFile.getFileObjectKey(),
                processFile.getFileType(),
                ProcessFileStatus.VALIDATED,
                processFile.getFilename(),
                processFile.getDocumentId(),
                processFile.getLastModificationDate());
    }

    ProcessEventDto createDtoFromEntity(ProcessEvent processEvent) {
        return new ProcessEventDto(processEvent.getTimestamp(),
                processEvent.getLevel(),
                processEvent.getMessage(),
                processEvent.getServiceName());
    }

    ProcessRunDto createDtoFromEntity(ProcessRun processRun) {
        List<ProcessFileDto> processFileDtos = processRun.getInputFiles().stream()
                .map(this::createDtoFromEntity)
                .toList();
        return new ProcessRunDto(processRun.getId(), processRun.getExecutionDate(), processFileDtos);
    }
}
