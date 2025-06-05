/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskLogEventUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessEventRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@SpringBootTest
class EventHandlerTest {

    @MockitoBean
    private TaskUpdateNotifier taskUpdateNotifier;

    @MockitoBean
    private StreamBridge streamBridge; // Useful to avoid AMQP connection that would fail

    @Autowired
    private EventHandler eventHandler;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProcessEventRepository processEventRepository;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        processEventRepository.deleteAll();
    }

    @Test
    void consumeTaskEventUpdateTest() {
        String logEvent = """
            {
              "gridcapa-task-id": "1fdda469-53e9-4d63-a533-b935cffdd2f2",
              "timestamp": "2023-06-07T13:14:50.106608Z",
              "level": "INFO",
              "message": "Hello World!",
              "serviceName": "GRIDCAPA"
            }""";
        Flux<List<byte[]>> logEventBytesFlux = Flux.fromStream(Stream.of(List.of(logEvent.getBytes())));
        Consumer<Flux<List<byte[]>>> fluxConsumer = eventHandler.consumeTaskEventUpdate();
        Assertions.assertDoesNotThrow(() -> fluxConsumer.accept(logEventBytesFlux));
    }

    @Test
    void handleTaskEventBatchUpdateTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        task.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f6"));
        taskRepository.save(task);
        String logEvent = """
            {
              "gridcapa-task-id": "1fdda469-53e9-4d63-a533-b935cffdd2f6",
              "timestamp": "2021-12-30T17:31:33.030+01:00",
              "level": "INFO",
              "message": "Hello from backend",
              "serviceName": "GRIDCAPA"
            }""";
        List<TaskLogEventUpdate> taskLogEventUpdates =  eventHandler.mapMessagesToListEvents(List.of(logEvent.getBytes()));
        eventHandler.handleTaskEventBatchUpdate(taskLogEventUpdates);
        final List<ProcessEvent> processEvents = processEventRepository.findAll();
        assertEquals(1, processEvents.size());
        ProcessEvent event = processEvents.get(0);
        assertEquals(OffsetDateTime.parse("2021-12-30T16:31:33.030Z"), event.getTimestamp());
        assertEquals("INFO", event.getLevel());
        assertEquals("Hello from backend", event.getMessage());
    }

    @Test
    void handleTaskEventBatchUpdateWithPrefixTest() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        task.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f6"));
        taskRepository.save(task);
        String logEvent = """
            {
              "gridcapa-task-id": "1fdda469-53e9-4d63-a533-b935cffdd2f6",
              "timestamp": "2021-12-30T17:31:33.030+01:00",
              "level": "INFO",
              "message": "Hello from backend",
              "serviceName": "GRIDCAPA" ,
              "eventPrefix": "STEP-1"
            }""";
        List<TaskLogEventUpdate> taskLogEventUpdates =  eventHandler.mapMessagesToListEvents(List.of(logEvent.getBytes()));
        eventHandler.handleTaskEventBatchUpdate(taskLogEventUpdates);
        final List<ProcessEvent> processEvents = processEventRepository.findAll();
        assertEquals(1, processEvents.size());
        ProcessEvent event = processEvents.get(0);
        assertEquals(OffsetDateTime.parse("2021-12-30T16:31:33.030Z"), event.getTimestamp());
        assertEquals("INFO", event.getLevel());
        assertEquals("[STEP-1] : Hello from backend", event.getMessage());
    }

    @Test
    void handleTaskEventBatchUpdateWithEventsList() {
        OffsetDateTime taskTimestamp = OffsetDateTime.parse("2021-10-01T21:00Z");
        Task task = new Task(taskTimestamp);
        task.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f6"));
        taskRepository.save(task);
        String logEvent1 = """
            {
              "gridcapa-task-id": "1fdda469-53e9-4d63-a533-b935cffdd2f6",
              "timestamp": "2021-12-30T17:31:33.030+01:00",
              "level": "INFO",
              "message": "Hello from backend",
              "serviceName": "GRIDCAPA",
              "eventPrefix": "STEP-1"
            }""";
        String logEvent2 = """
            {
              "gridcapa-task-id": "1fdda469-53e9-4d63-a533-b935cffdd2f6",
              "timestamp": "2021-12-30T17:31:34.030+01:00",
              "level": "WARNING",
              "message": "Hello from backend2",
              "serviceName": "GRIDCAPA"
            }""";
        List<TaskLogEventUpdate> taskLogEventUpdates =  eventHandler.mapMessagesToListEvents(List.of(logEvent1.getBytes(), logEvent2.getBytes()));
        eventHandler.handleTaskEventBatchUpdate(taskLogEventUpdates);
        final List<ProcessEvent> processEvents = processEventRepository.findAll();
        assertEquals(2, processEvents.size());
        ProcessEvent event0 = processEvents.get(0);
        ProcessEvent event1 = processEvents.get(1);

        assertEquals(OffsetDateTime.parse("2021-12-30T16:31:33.030Z"), event0.getTimestamp());
        assertEquals("INFO", event0.getLevel());
        assertEquals("[STEP-1] : Hello from backend", event0.getMessage());

        assertEquals(OffsetDateTime.parse("2021-12-30T16:31:34.030Z"), event1.getTimestamp());
        assertEquals("WARNING", event1.getLevel());
        assertEquals("Hello from backend2", event1.getMessage());
    }

    @Test
    void mapMessageToEventErrorTest() {
        Assertions.assertNull(eventHandler.mapMessageToEvent("random"));
    }
}
