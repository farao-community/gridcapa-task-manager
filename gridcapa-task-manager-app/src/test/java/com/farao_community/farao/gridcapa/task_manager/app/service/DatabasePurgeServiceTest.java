/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessEventRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */
@SpringBootTest
class DatabasePurgeServiceTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProcessEventRepository processEventRepository;

    @MockitoBean
    private StreamBridge streamBridge; // Useful to avoid AMQP connection that would fail

    @Autowired
    private DatabasePurgeService databasePurgeService;

    @Autowired
    private EventHandler eventHandler;

    @Test
    void scheduledDatabaseTaskEventsPurgeTest() {
        OffsetDateTime offsetDateTimeNow = OffsetDateTime.now(ZoneId.of("UTC"));
        offsetDateTimeNow = offsetDateTimeNow.withNano(0);
        OffsetDateTime offsetDateTimeTask1 = offsetDateTimeNow.minusDays(11);
        OffsetDateTime offsetDateTimeTask2 = offsetDateTimeNow.minusDays(9);
        OffsetDateTime offsetDateTimeEvent11 = offsetDateTimeNow.minusDays(10);
        OffsetDateTime offsetDateTimeEvent12 = offsetDateTimeNow.minusDays(9);
        OffsetDateTime offsetDateTimeEvent21 = offsetDateTimeNow.minusDays(8);
        OffsetDateTime offsetDateTimeEvent22 = offsetDateTimeNow.minusDays(6);

        Task task1 = new Task(offsetDateTimeTask1);
        task1.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f1"));
        taskRepository.save(task1);
        Task task2 = new Task(offsetDateTimeTask2);
        task2.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f2"));
        taskRepository.save(task2);

        String logEvent11 = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f1\",\n" +
                "  \"timestamp\": \"" + offsetDateTimeEvent11 + "\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend11\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        String logEvent12 = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f1\",\n" +
                "  \"timestamp\": \"" + offsetDateTimeEvent12 + "\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend12\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        String logEvent21 = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f2\",\n" +
                "  \"timestamp\": \"" + offsetDateTimeEvent21 + "\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend21\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        String logEvent22 = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f2\",\n" +
                "  \"timestamp\": \"" + offsetDateTimeEvent22 + "\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend22\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        List<byte[]> logEventBytes = List.of(logEvent11.getBytes(), logEvent12.getBytes(), logEvent21.getBytes(), logEvent22.getBytes());
        Flux<List<byte[]>> logEventBytesFlux = Flux.fromStream(Stream.of(logEventBytes));
        eventHandler.consumeTaskEventUpdate().accept(logEventBytesFlux);

        final List<ProcessEvent> processEventsListBeforePurge = processEventRepository.findAll();
        final List<ProcessEvent> task1ProcessEventBeforePurge = processEventsListBeforePurge.stream().filter(pe -> pe.getTask().getId().equals(task1.getId())).toList();
        final List<ProcessEvent> task2ProcessEventBeforePurge = processEventsListBeforePurge.stream().filter(pe -> pe.getTask().getId().equals(task2.getId())).toList();
        assertEquals(2, task1ProcessEventBeforePurge.size());
        assertEquals(2, task2ProcessEventBeforePurge.size());

        databasePurgeService.scheduledDatabaseTaskEventsPurge();
        final List<ProcessEvent> processEventsListAfterPurge = processEventRepository.findAll();
        final List<ProcessEvent> task1ProcessEventAfterPurge = processEventsListAfterPurge.stream().filter(pe -> pe.getTask().getId().equals(task1.getId())).toList();
        final List<ProcessEvent> task2ProcessEventAfterPurge = processEventsListAfterPurge.stream().filter(pe -> pe.getTask().getId().equals(task2.getId())).toList();
        assertEquals(0, task1ProcessEventAfterPurge.size());
        assertEquals(1, task2ProcessEventAfterPurge.size());
        assertEquals(1, processEventsListAfterPurge.size());
    }
}
