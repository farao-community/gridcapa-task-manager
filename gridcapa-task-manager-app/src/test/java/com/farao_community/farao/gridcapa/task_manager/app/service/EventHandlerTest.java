/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.farao_community.farao.gridcapa.task_manager.api.TaskLogEventUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    final static String TASK_LOG_EVENT_1_JSON_STR = """
            {
              "gridcapa-task-id": "1fdda469-53e9-4d63-a533-b935cffdd2f2",
              "timestamp": "2023-06-07T13:14:50.106608Z",
              "level": "INFO",
              "message": "Hello World!",
              "serviceName": "GRIDCAPA"
            }""";

    final static TaskLogEventUpdate TASK_LOG_EVENT_1 = new TaskLogEventUpdate(
            "1fdda469-53e9-4d63-a533-b935cffdd2f2",
            "2023-06-07T13:14:50.106608Z",
            "INFO",
            "Hello World!",
            "GRIDCAPA"
    );

    @Test
    void consumeTaskEventUpdateTest() {
        Flux<List<byte[]>> logEventBytesFlux = Flux.fromStream(Stream.of(List.of(TASK_LOG_EVENT_1_JSON_STR.getBytes())));
        Consumer<Flux<List<byte[]>>> fluxConsumer = eventHandler.consumeTaskEventUpdate();
        assertThatCode(() -> fluxConsumer.accept(logEventBytesFlux))
                .doesNotThrowAnyException();
    }

    @Test
    void consumeTaskEventUpdateErrorTest() {
        final Logger logger = (Logger) LoggerFactory.getLogger(EventHandler.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        // Forcing error on Method called in eventHandler.consumeTaskEventUpdate() to test Exception handling
        when(taskRepository.saveAll(any())).thenThrow(new RuntimeException("Database error"));

        Flux<List<byte[]>> logEventBytesFlux = Flux.fromStream(Stream.of(List.of(TASK_LOG_EVENT_1_JSON_STR.getBytes())));
        Consumer<Flux<List<byte[]>>> fluxConsumer = eventHandler.consumeTaskEventUpdate();
        fluxConsumer.accept(logEventBytesFlux);

        final List<ILoggingEvent> logs = appender.list;
        // A warn log is thrown as no Task is defined additionally to the error log
        assertThat(logs).
                hasSize(2);
        // Log Error from consumeTaskEventUpdate method
        assertThat(logs.get(1).getFormattedMessage())
                .contains("Unable to handle task events update properly");
    }

    @Test
    void mapMessageToEventListErrorTest() {
        final Logger logger = (Logger) LoggerFactory.getLogger(EventHandler.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        eventHandler.mapMessagesToListEvents(List.of("test Exception".getBytes()));

        final List<ILoggingEvent> logs = appender.list;
        assertThat(logs)
                .hasSize(1);
        assertThat(logs.getFirst().getFormattedMessage())
                .contains("Couldn't parse log event, Impossible to match the event with concerned task");
    }

    @Test
    void handleTaskEventBatchUpdateTest() {
        final OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        final Task task = new Task(taskTimestamp);
        final UUID taskId = UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f2");
        task.setId(taskId);
        when(taskRepository.findByIdAndFetchProcessFiles(taskId)).thenReturn(Optional.of(task));

        eventHandler.handleTaskEventBatchUpdate(List.of(TASK_LOG_EVENT_1));

        verify(taskService, times(1)).addProcessEventToTask(TASK_LOG_EVENT_1, task);
    }

    @Test
    void handleTaskEventBatchUpdateWithPrefixTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        final UUID taskId = UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f6");
        task.setId(taskId);
        final TaskLogEventUpdate logEvent = new TaskLogEventUpdate(
                "1fdda469-53e9-4d63-a533-b935cffdd2f6",
                "2023-06-07T13:14:50.106608Z",
                "INFO",
                "Prefix Test",
                "GRIDCAPA",
                "STEP-1"
        );
        when(taskRepository.findByIdAndFetchProcessFiles(taskId)).thenReturn(Optional.of(task));

        eventHandler.handleTaskEventBatchUpdate(List.of(logEvent));

        verify(taskService, times(1)).addProcessEventToTask(logEvent, task);
    }

    @Test
    void handleTaskEventBatchUpdateWithEventsList() {
        final OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        final Task task1 = new Task(taskTimestamp);
        final UUID taskId1 = UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f2");
        task1.setId(taskId1);
        final Task task2 = new Task(taskTimestamp);
        final UUID taskId2 = UUID.fromString("ab475df8-53e9-4d63-a533-b935cffdd2f6");
        task2.setId(taskId2);
        when(taskRepository.findByIdAndFetchProcessFiles(taskId1)).thenReturn(Optional.of(task1));
        when(taskRepository.findByIdAndFetchProcessFiles(taskId2)).thenReturn(Optional.of(task2));

        final TaskLogEventUpdate logEvent2 = new TaskLogEventUpdate(
                "ab475df8-53e9-4d63-a533-b935cffdd2f6",
                "2023-06-07T13:14:50.106608Z",
                "WARN",
                "Test Event 2",
                "GRIDCAPA"
        );
        eventHandler.handleTaskEventBatchUpdate(List.of(TASK_LOG_EVENT_1, logEvent2));

        verify(taskService, times(1)).addProcessEventToTask(TASK_LOG_EVENT_1, task1);
        verify(taskService, times(1)).addProcessEventToTask(logEvent2, task2);
    }

    @Test
    void handleTaskEventBatchUpdateErrorTest() {
        final Logger logger = (Logger) LoggerFactory.getLogger(EventHandler.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        final OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        final Task task = new Task(taskTimestamp);
        final UUID taskId = UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f2");
        task.setId(taskId);
        when(taskRepository.findByIdAndFetchProcessFiles(taskId)).thenReturn(Optional.of(task));

        final TaskLogEventUpdate logEventFail = new TaskLogEventUpdate(
                "ab475df8-53e9-4d63-a533-",
                "2023-06-07T13:14:50.106608Z",
                "WARN",
                "Test Event 2",
                "GRIDCAPA"
        );
        eventHandler.handleTaskEventBatchUpdate(List.of(TASK_LOG_EVENT_1, logEventFail));

        // Verify logEvent1 was processed
        verify(taskService, times(1)).addProcessEventToTask(TASK_LOG_EVENT_1, task);
        // Verify logEventFail logs an error with incorrect gridcapa-task-id
        final List<ILoggingEvent> logs = appender.list;
        assertThat(logs)
                .hasSize(1);
        assertThat(logs.getFirst().getFormattedMessage())
                .contains("Unable to handle task event");
    }
}
