/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.farao_community.farao.gridcapa.task_manager.api.TaskLogEventUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@ExtendWith(MockitoExtension.class)
class EventHandlerTest {

    @InjectMocks
    private EventHandler eventHandler;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskUpdateNotifier taskUpdateNotifier;

    @Mock
    private TaskService taskService;

    final String logEvent1 = """
            {
              "gridcapa-task-id": "1fdda469-53e9-4d63-a533-b935cffdd2f2",
              "timestamp": "2023-06-07T13:14:50.106608Z",
              "level": "INFO",
              "message": "Hello World!",
              "serviceName": "GRIDCAPA"
            }""";

    @Test
    void consumeTaskEventUpdateTest() {
        Flux<List<byte[]>> logEventBytesFlux = Flux.fromStream(Stream.of(List.of(logEvent1.getBytes())));
        Consumer<Flux<List<byte[]>>> fluxConsumer = eventHandler.consumeTaskEventUpdate();
        Assertions.assertDoesNotThrow(() -> fluxConsumer.accept(logEventBytesFlux));
    }

    @Test
    void consumeTaskEventUpdateErrorTest() {
        when(taskRepository.saveAll(any())).thenThrow(new RuntimeException("Database error"));

        Flux<List<byte[]>> logEventBytesFlux = Flux.fromStream(Stream.of(List.of(logEvent1.getBytes())));
        Consumer<Flux<List<byte[]>>> fluxConsumer = eventHandler.consumeTaskEventUpdate();

        Logger logger = (Logger) LoggerFactory.getLogger(EventHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        fluxConsumer.accept(logEventBytesFlux);

        List<ILoggingEvent> logs = appender.list;
        assertThat(logs)
                .filteredOn(log -> log.getLevel() == Level.ERROR)
                .hasSize(1);
        // Log Error from consumeTaskEventUpdate method
        assertThat(logs.get(1).getFormattedMessage())
                .contains("Unable to handle task events update properly");
    }

    @Test
    void mapMessageToEventErrorTest() {
        Assertions.assertNull(eventHandler.mapMessageToEvent("random"));

        Logger logger = (Logger) LoggerFactory.getLogger(EventHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        String logEvent = "test Exception";
        eventHandler.mapMessagesToListEvents(List.of(logEvent.getBytes()));

        List<ILoggingEvent> logs = appender.list;
        assertThat(logs)
                .filteredOn(log -> log.getLevel() == Level.WARN)
                .hasSize(1);
        assertThat(logs.getFirst().getFormattedMessage())
                .contains("Couldn't parse log event, Impossible to match the event with concerned task");
    }

    @Test
    void handleTaskEventBatchUpdateTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        UUID taskId = UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f2");
        task.setId(taskId);
        when(taskRepository.findByIdAndFetchProcessFiles(taskId)).thenReturn(Optional.of(task));

        List<TaskLogEventUpdate> taskLogEventUpdates = eventHandler.mapMessagesToListEvents(List.of(logEvent1.getBytes()));
        eventHandler.handleTaskEventBatchUpdate(taskLogEventUpdates);
        final TaskLogEventUpdate event = taskLogEventUpdates.getFirst();

        verify(taskService, times(1)).addProcessEventToTask(event, task);
    }

    @Test
    void handleTaskEventBatchUpdateWithEventsList() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task1 = new Task(taskTimestamp);
        UUID taskId = UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f2");
        task1.setId(taskId);
        Task task2 = new Task(taskTimestamp);
        UUID taskId2 = UUID.fromString("ab475df8-53e9-4d63-a533-b935cffdd2f6");
        task2.setId(taskId2);
        when(taskRepository.findByIdAndFetchProcessFiles(taskId)).thenReturn(Optional.of(task1));
        when(taskRepository.findByIdAndFetchProcessFiles(taskId2)).thenReturn(Optional.of(task2));

        String logEvent2 = """
                {
                  "gridcapa-task-id": "ab475df8-53e9-4d63-a533-b935cffdd2f6",
                  "timestamp": "2021-12-30T17:31:34.030+01:00",
                  "level": "WARNING",
                  "message": "Hello from backend2",
                  "serviceName": "GRIDCAPA"
                }""";

        List<TaskLogEventUpdate> taskLogEventUpdates = eventHandler.mapMessagesToListEvents(List.of(logEvent1.getBytes(), logEvent2.getBytes()));
        eventHandler.handleTaskEventBatchUpdate(taskLogEventUpdates);
        List<Task> tasks = new ArrayList<>();
        tasks.add(task1);
        tasks.add(task2);
        for (int i = 0; i < tasks.size(); i++) {
            verify(taskService, times(1)).addProcessEventToTask(taskLogEventUpdates.get(i), tasks.get(i));
        }
    }

    @Test
    void handleTaskEventBatchUpdateErrorTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        UUID taskId = UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f2");
        task.setId(taskId);
        when(taskRepository.findByIdAndFetchProcessFiles(taskId)).thenReturn(Optional.of(task));

        String logEvent2 = """
                {
                  "gridcapa-task-id": "ab475df8-53e9-4d63-a533-",
                  "timestamp": "2021-12-30T17:31:34.030+01:00",
                  "level": "WARNING",
                  "message": "Hello from backend2",
                  "serviceName": "GRIDCAPA"
                }""";

        List<TaskLogEventUpdate> taskLogEventUpdates = eventHandler.mapMessagesToListEvents(List.of(logEvent1.getBytes(), logEvent2.getBytes()));

        Logger logger = (Logger) LoggerFactory.getLogger(EventHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        eventHandler.handleTaskEventBatchUpdate(taskLogEventUpdates);

        // Verify event1 was processed
        final TaskLogEventUpdate event1 = taskLogEventUpdates.getFirst();
        verify(taskService, times(1)).addProcessEventToTask(event1, task);

        // Verify event2 logs an error with incorrect gridcapa-task-id
        List<ILoggingEvent> logs = appender.list;
        assertThat(logs)
                .filteredOn(log -> log.getLevel() == Level.ERROR)
                .hasSize(1);
        assertThat(logs.getFirst().getFormattedMessage())
                .contains("Unable to handle task event");
    }
}
