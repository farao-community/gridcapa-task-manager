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
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessRun;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public TaskDtoBuilderService(final TaskManagerConfigurationProperties properties,
                                 final TaskRepository taskRepository,
                                 final ParameterService parameterService) {
        this.properties = properties;
        this.taskRepository = taskRepository;
        this.parameterService = parameterService;
        this.localZone = ZoneId.of(this.properties.getProcess().getTimezone());
    }

    /**
     * To use only if Process Events are required in the process. Otherwise, prefer the method getTaskDtoWithoutProcessEvents.
     *
     * @param timestamp
     * @return
     */
    public TaskDto getTaskDtoWithProcessEvents(final OffsetDateTime timestamp) {
        return taskRepository.findByTimestampAndFetchProcessEvents(timestamp)
                .map(this::createDtoFromEntity)
                .orElse(getEmptyTask(timestamp));
    }

    /**
     * To use preferably, as it does not fetch process event hence reducing memory used.
     *
     * @param timestamp
     * @return
     */
    public TaskDto getTaskDtoWithoutProcessEvents(final OffsetDateTime timestamp) {
        return taskRepository.findByTimestamp(timestamp)
                .map(this::createDtoFromEntityWithoutProcessEvents)
                .orElse(getEmptyTask(timestamp));
    }

    public List<TaskDto> getTasksDtos(final LocalDate businessDate) {
        final LocalDateTime startLdt = businessDate.atTime(0, 30);
        final LocalDateTime endLdt = businessDate.atTime(23, 30);
        final ZoneOffset startOffset = localZone.getRules().getOffset(startLdt);
        final ZoneOffset endOffset = localZone.getRules().getOffset(endLdt);
        final OffsetDateTime startTimestamp = startLdt.atOffset(startOffset);
        final OffsetDateTime endTimestamp = endLdt.atOffset(endOffset);

        final Set<Task> tasks = taskRepository.findAllByTimestampBetweenForBusinessDayView(startTimestamp, endTimestamp);
        final Map<OffsetDateTime, TaskDto> taskMap = new HashMap<>();
        for (OffsetDateTime loopTimestamp = startTimestamp;
             !loopTimestamp.isAfter(endTimestamp);
             loopTimestamp = loopTimestamp.plusHours(1).atZoneSameInstant(localZone).toOffsetDateTime()
        ) {
            final OffsetDateTime taskTimeStamp = loopTimestamp.atZoneSameInstant(UTC_ZONE).toOffsetDateTime();
            taskMap.put(taskTimeStamp, getEmptyTask(taskTimeStamp));
        }

        tasks.stream()
                .map(this::createDtoFromEntityWithoutProcessEvents)
                .forEach(dto -> taskMap.put(dto.getTimestamp(), dto));

        return taskMap.values().stream().toList();
    }

    public List<TaskDto> getListRunningTasksDto() {
        return taskRepository.findAllRunningAndPending().stream()
                .map(this::createDtoFromEntityWithoutProcessEvents)
                .toList();
    }

    public TaskDto getEmptyTask(final OffsetDateTime timestamp) {
        return TaskDto.emptyTask(
                timestamp,
                properties.getProcess().getInputs(),
                properties.getProcess().getOutputs());
    }

    public TaskDto createDtoFromEntity(final Task task) {
        return createDtoFromEntityGivenEvents(task, true);
    }

    public TaskDto createDtoFromEntityWithoutProcessEvents(final Task task) {
        return createDtoFromEntityGivenEvents(task, false);
    }

    private TaskDto createDtoFromEntityGivenEvents(final Task task,
                                                   final boolean withEvents) {

        final Function<String, Stream<ProcessFileDto>> mapInputFile = str -> task.getInput(str)
                .stream()
                .map(this::createDtoFromEntity);

        final Function<String, ProcessFileDto> mapInputFileOrElseEmpty = str -> task.getInput(str)
                .map(this::createDtoFromEntity)
                .orElseGet(() -> ProcessFileDto.emptyProcessFile(str));

        List<ProcessFileDto> inputs = properties.getProcess().getInputs().stream()
                .map(mapInputFileOrElseEmpty)
                .collect(Collectors.toList());

        List<ProcessFileDto> availableInputs = properties.getProcess().getInputs()
                .stream()
                .flatMap(str -> task.getAvailableInputs(str)
                            .stream()
                            .map(this::createDtoFromEntity))
                .collect(Collectors.toList());

        List<ProcessFileDto> optionalInputs = properties.getProcess().getOptionalInputs().stream()
                .map(mapInputFileOrElseEmpty)
                .toList();
        inputs.addAll(optionalInputs);

        List<ProcessFileDto> availableOptionalInputs = properties.getProcess().getOptionalInputs().stream()
                .flatMap(mapInputFile)
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

    ProcessFileDto createDtoFromEntity(final ProcessFile processFile) {
        return new ProcessFileDto(
                processFile.getFileObjectKey(),
                processFile.getFileType(),
                ProcessFileStatus.VALIDATED,
                processFile.getFilename(),
                processFile.getDocumentId(),
                processFile.getLastModificationDate());
    }

    ProcessEventDto createDtoFromEntity(final ProcessEvent processEvent) {
        return new ProcessEventDto(processEvent.getTimestamp(),
                                   processEvent.getLevel(),
                                   processEvent.getMessage(),
                                   processEvent.getServiceName());
    }

    ProcessRunDto createDtoFromEntity(final ProcessRun processRun) {
        final List<ProcessFileDto> processFileDtos = processRun
                .getInputFiles()
                .stream()
                .map(this::createDtoFromEntity)
                .toList();
        return new ProcessRunDto(processRun.getId(), processRun.getExecutionDate(), processFileDtos);
    }
}
