/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ParameterDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessEventDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
@SpringBootTest
class TaskDtoBuilderServiceTest {

    @MockBean
    private TaskRepository taskRepository;
    @Autowired
    private TaskDtoBuilderService taskDtoBuilderService;

    @Test
    void getEmptyTaskTest() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:18Z");

        TaskDto taskDto = taskDtoBuilderService.getEmptyTask(timestamp);

        Assertions.assertThat(taskDto).isNotNull();
        Assertions.assertThat(taskDto.getId()).isNotNull();
        Assertions.assertThat(taskDto.getTimestamp()).isEqualTo(timestamp);
        Assertions.assertThat(taskDto.getStatus()).isEqualTo(TaskStatus.NOT_CREATED);
        Assertions.assertThat(taskDto.getProcessEvents()).isEmpty();
        Assertions.assertThat(taskDto.getInputs()).hasSize(2);
        Assertions.assertThat(taskDto.getAvailableInputs()).isEmpty();
        Assertions.assertThat(taskDto.getOutputs()).hasSize(1);
    }

    @Test
    void getTaskDtoNoTaskInDatabaseTest() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:18Z");
        Mockito.when(taskRepository.findByTimestamp(timestamp)).thenReturn(Optional.empty());

        TaskDto taskDto = taskDtoBuilderService.getTaskDto(timestamp);

        Assertions.assertThat(taskDto).isNotNull();
        Assertions.assertThat(taskDto.getTimestamp()).isEqualTo(timestamp);
        Assertions.assertThat(taskDto.getStatus()).isEqualTo(TaskStatus.NOT_CREATED);
    }

    @Test
    void getTaskDtoFromDatabaseTest() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:18Z");
        Task task = new Task(timestamp);
        task.setStatus(TaskStatus.READY);
        Mockito.when(taskRepository.findByTimestamp(timestamp)).thenReturn(Optional.of(task));

        TaskDto taskDto = taskDtoBuilderService.getTaskDto(timestamp);

        Assertions.assertThat(taskDto).isNotNull();
        Assertions.assertThat(taskDto.getTimestamp()).isEqualTo(timestamp);
        Assertions.assertThat(taskDto.getStatus()).isEqualTo(TaskStatus.READY);
    }

    @Test
    void createDtoFromEntityTaskTest() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:18Z");
        UUID uuid = UUID.randomUUID();
        TaskStatus status = TaskStatus.NOT_CREATED;
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                timestamp,
                timestamp.plusHours(1),
                timestamp);
        ProcessFile processFileOutput = new ProcessFile(
                "cne-file",
                "output",
                "CNE",
                timestamp,
                timestamp.plusHours(1),
                timestamp);

        Task task = new Task(timestamp);
        task.setId(uuid);
        task.setStatus(status);
        task.addProcessEvent(timestamp, "info", "message", "serviceName");
        task.addProcessFile(processFileInput);
        task.addProcessFile(processFileOutput);

        TaskDto taskDto = taskDtoBuilderService.createDtoFromEntity(task);

        Assertions.assertThat(taskDto).isNotNull();
        Assertions.assertThat(taskDto.getId()).isEqualTo(uuid);
        Assertions.assertThat(taskDto.getStatus()).isEqualTo(status);
        Assertions.assertThat(taskDto.getTimestamp()).isEqualTo(timestamp);
        Assertions.assertThat(taskDto.getProcessEvents()).hasSize(1);
        Assertions.assertThat(taskDto.getInputs()).hasSize(2);
        Assertions.assertThat(taskDto.getAvailableInputs()).hasSize(1);
        Assertions.assertThat(taskDto.getOutputs()).hasSize(1);
    }

    @Test
    void createDtoFromEntityTaskNoLogsTest() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:18Z");
        UUID uuid = UUID.randomUUID();
        TaskStatus status = TaskStatus.NOT_CREATED;
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                timestamp,
                timestamp.plusHours(1),
                timestamp);
        ProcessFile processFileOutput = new ProcessFile(
                "cne-file",
                "output",
                "CNE",
                timestamp,
                timestamp.plusHours(1),
                timestamp);

        Task task = new Task(timestamp);
        task.setId(uuid);
        task.setStatus(status);
        task.addProcessEvent(timestamp, "info", "message", "serviceName");
        task.addProcessFile(processFileInput);
        task.addProcessFile(processFileOutput);

        TaskDto taskDto = taskDtoBuilderService.createDtoFromEntityNoLogs(task);

        Assertions.assertThat(taskDto).isNotNull();
        Assertions.assertThat(taskDto.getId()).isEqualTo(uuid);
        Assertions.assertThat(taskDto.getStatus()).isEqualTo(status);
        Assertions.assertThat(taskDto.getTimestamp()).isEqualTo(timestamp);
        Assertions.assertThat(taskDto.getProcessEvents()).isEmpty();
        Assertions.assertThat(taskDto.getInputs()).hasSize(2);
        Assertions.assertThat(taskDto.getAvailableInputs()).hasSize(1);
        Assertions.assertThat(taskDto.getOutputs()).hasSize(1);
    }

    @Test
    void createDtoFromEntityProcessFileTest() {
        String fileType = "CGM";
        String filename = "cgm-name";
        String filePath = "path/to/" + filename;
        OffsetDateTime modificationDate = OffsetDateTime.parse("2021-10-11T10:18Z");
        ProcessFile processFile = new ProcessFile(
                filePath,
                "input",
                fileType,
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                modificationDate);

        ProcessFileDto processFileDto = taskDtoBuilderService.createDtoFromEntity(processFile);

        Assertions.assertThat(processFileDto).isNotNull();
        Assertions.assertThat(processFileDto.getFileType()).isEqualTo(fileType);
        Assertions.assertThat(processFileDto.getFilePath()).isEqualTo(filePath);
        Assertions.assertThat(processFileDto.getFilename()).isEqualTo(filename);
        Assertions.assertThat(processFileDto.getProcessFileStatus()).isEqualTo(ProcessFileStatus.VALIDATED);
        Assertions.assertThat(processFileDto.getLastModificationDate()).isEqualTo(modificationDate);
    }

    @Test
    void createDtoFromEntityProcessEventTest() {
        OffsetDateTime now = OffsetDateTime.now();
        String level = "INFO";
        String message = "CGM arrived";
        String serviceName = "serviceName";
        ProcessEvent processEvent = new ProcessEvent(null, now, level, message, serviceName);

        ProcessEventDto processEventDto = taskDtoBuilderService.createDtoFromEntity(processEvent);

        Assertions.assertThat(processEventDto.getLevel()).isEqualTo(level);
        Assertions.assertThat(processEvent.getTimestamp()).isEqualTo(now);
        Assertions.assertThat(processEventDto.getMessage()).isEqualTo(message);
        Assertions.assertThat(processEventDto.getServiceName()).isEqualTo(serviceName);
    }

    @Test
    void getListRunningTaskTest() {
        Task task1 = new Task();
        Task task2 = new Task();
        Mockito.when(taskRepository.findAllRunningAndPending()).thenReturn(Set.of(task1, task2));

        List<TaskDto> runningTasks = taskDtoBuilderService.getListRunningTasksDto();

        Assertions.assertThat(runningTasks).hasSize(2);
    }

    @Test
    void testGetListTasksDto24TS() {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = Mockito.mock(TaskManagerConfigurationProperties.ProcessProperties.class);
        Mockito.when(processProperties.getTimezone()).thenReturn("CET");
        TaskManagerConfigurationProperties properties = new TaskManagerConfigurationProperties(processProperties, new ArrayList<>());
        TaskRepository taskRepository = new TaskRepositoryMock();
        ParameterService parameterService = Mockito.mock(ParameterService.class);
        ParameterDto param = new ParameterDto(null, null, 1, null, null, 2, null, null);
        Mockito.when(parameterService.getParameters()).thenReturn(List.of(param, param, param));
        TaskDtoBuilderService taskDtoBuilderService = new TaskDtoBuilderService(properties, taskRepository, parameterService);
        LocalDate localDate = LocalDate.of(2023, 11, 9);
        List<TaskDto> listTasksDto = taskDtoBuilderService.getListTasksDto(localDate);
        assertEquals(24, listTasksDto.size());
        assertEquals(3, listTasksDto.get(0).getParameters().size());
    }

    @Test
    void testGetListTasksDto23TS() {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = Mockito.mock(TaskManagerConfigurationProperties.ProcessProperties.class);
        Mockito.when(processProperties.getTimezone()).thenReturn("CET");
        TaskManagerConfigurationProperties properties = new TaskManagerConfigurationProperties(processProperties, new ArrayList<>());
        TaskRepository taskRepository = new TaskRepositoryMock();
        ParameterService parameterService = Mockito.mock(ParameterService.class);
        TaskDtoBuilderService taskDtoBuilderService = new TaskDtoBuilderService(properties, taskRepository, parameterService);
        LocalDate localDate = LocalDate.of(2023, 3, 26);
        assertEquals(23, taskDtoBuilderService.getListTasksDto(localDate).size());
    }

    @Test
    void testGetListTasksDto25TS() {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = Mockito.mock(TaskManagerConfigurationProperties.ProcessProperties.class);
        Mockito.when(processProperties.getTimezone()).thenReturn("CET");
        TaskManagerConfigurationProperties properties = new TaskManagerConfigurationProperties(processProperties, new ArrayList<>());
        TaskRepository taskRepository = new TaskRepositoryMock();
        ParameterService parameterService = Mockito.mock(ParameterService.class);
        TaskDtoBuilderService taskDtoBuilderService = new TaskDtoBuilderService(properties, taskRepository, parameterService);
        LocalDate localDate = LocalDate.of(2023, 10, 29);
        assertEquals(25, taskDtoBuilderService.getListTasksDto(localDate).size());
    }

    private class TaskRepositoryMock implements TaskRepository {

        @Override
        public Optional<Task> findByIdWithProcessFiles(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<Task> findByTimestamp(OffsetDateTime timestamp) {
            return Optional.empty();
        }

        @Override
        public Set<Task> findAllByTimestampBetween(OffsetDateTime startingTimestamp, OffsetDateTime endingTimestamp) {
            Set<Task> tasks = new HashSet<>();
            OffsetDateTime time = startingTimestamp;
            while (time.isBefore(endingTimestamp)) {
                Task task = Mockito.mock(Task.class);
                Mockito.when(task.getTimestamp()).thenReturn(time);
                tasks.add(task);
            }
            return tasks;
        }

        @Override
        public Set<Task> findAllRunningAndPending() {
            return null;
        }

        @Override
        public Set<Task> findAllWithSomeProcessEvent() {
            return null;
        }

        @Override
        public Set<Task> findAllByTimestampBetweenForBusinessDayView(OffsetDateTime startingTimestamp, OffsetDateTime endingTimestamp) {
            Set<Task> tasks = new HashSet<>();
            OffsetDateTime time = startingTimestamp;
            while (!time.isAfter(endingTimestamp)) {
                Task task = Mockito.mock(Task.class);
                Mockito.when(task.getTimestamp()).thenReturn(time.atZoneSameInstant(ZoneId.of("Z")).toOffsetDateTime());
                tasks.add(task);
                time = time.plusHours(1);
            }
            return tasks;
        }

        @Override
        public List<Task> findAll() {
            return null;
        }

        @Override
        public List<Task> findAll(Sort sort) {
            return null;
        }

        @Override
        public Page<Task> findAll(Pageable pageable) {
            return null;
        }

        @Override
        public List<Task> findAllById(Iterable<UUID> uuids) {
            return null;
        }

        @Override
        public long count() {
            return 0;
        }

        @Override
        public void deleteById(UUID uuid) {

        }

        @Override
        public void delete(Task entity) {

        }

        @Override
        public void deleteAllById(Iterable<? extends UUID> uuids) {

        }

        @Override
        public void deleteAll(Iterable<? extends Task> entities) {

        }

        @Override
        public void deleteAll() {

        }

        @Override
        public <S extends Task> S save(S entity) {
            return null;
        }

        @Override
        public <S extends Task> List<S> saveAll(Iterable<S> entities) {
            return null;
        }

        @Override
        public Optional<Task> findById(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean existsById(UUID uuid) {
            return false;
        }

        @Override
        public void flush() {

        }

        @Override
        public <S extends Task> S saveAndFlush(S entity) {
            return null;
        }

        @Override
        public <S extends Task> List<S> saveAllAndFlush(Iterable<S> entities) {
            return null;
        }

        @Override
        public void deleteAllInBatch(Iterable<Task> entities) {

        }

        @Override
        public void deleteAllByIdInBatch(Iterable<UUID> uuids) {

        }

        @Override
        public void deleteAllInBatch() {

        }

        @Override
        public Task getOne(UUID uuid) {
            return null;
        }

        @Override
        public Task getById(UUID uuid) {
            return null;
        }

        @Override
        public Task getReferenceById(final UUID uuid) {
            return null;
        }

        @Override
        public <S extends Task> Optional<S> findOne(Example<S> example) {
            return Optional.empty();
        }

        @Override
        public <S extends Task> List<S> findAll(Example<S> example) {
            return null;
        }

        @Override
        public <S extends Task> List<S> findAll(Example<S> example, Sort sort) {
            return null;
        }

        @Override
        public <S extends Task> Page<S> findAll(Example<S> example, Pageable pageable) {
            return null;
        }

        @Override
        public <S extends Task> long count(Example<S> example) {
            return 0;
        }

        @Override
        public <S extends Task> boolean exists(Example<S> example) {
            return false;
        }

        @Override
        public <S extends Task, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            return null;
        }
    }
}
