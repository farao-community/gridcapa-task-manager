/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ParameterDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskDtoBuilderServiceTest {

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
