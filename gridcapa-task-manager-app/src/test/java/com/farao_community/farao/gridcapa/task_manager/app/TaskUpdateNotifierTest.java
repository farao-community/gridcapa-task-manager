/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.WebsocketConfig;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.service.TaskDtoBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskUpdateNotifierTest {

    @Mock
    private StreamBridge streamBridge;

    @Mock
    private TaskDtoBuilderService taskDtoBuilderService;

    @Mock
    private SimpMessagingTemplate stompBridge;

    @Mock
    private WebsocketConfig websocketConfig;

    @InjectMocks
    private TaskUpdateNotifier taskUpdateNotifier;

    private Task task;
    private TaskDto taskDto;

    @BeforeEach
    void setUp() {
        task = mock(Task.class);
        taskDto = mock(TaskDto.class);
        when(taskDtoBuilderService.createDtoFromEntityWithoutProcessEvents(any(Task.class))).thenReturn(taskDto);
        when(task.getTimestamp()).thenReturn(OffsetDateTime.now());
        when(websocketConfig.getNotify()).thenReturn("/topic");
    }

    @Test
    void testNotifyWithStatusUpdate() {
        // Given
        final boolean withStatusUpdate = true;
        final boolean withEventsUpdate = false;
        final boolean withNewInput = false;

        // When
        taskUpdateNotifier.notify(task, withStatusUpdate, withEventsUpdate, withNewInput);

        // Then
        verify(streamBridge, times(1)).send("task-status-updated", taskDto);
        verify(stompBridge, times(2)).convertAndSend(anyString(), eq(taskDto));
        verify(stompBridge, never()).convertAndSend(contains("/events"), anyBoolean());
    }

    @Test
    void testNotifyWithNewInput() {
        // Given
        final boolean withStatusUpdate = false;
        final boolean withEventsUpdate = false;
        final boolean withNewInput = true;

        // When
        taskUpdateNotifier.notify(task, withStatusUpdate, withEventsUpdate, withNewInput);

        // Then
        verify(streamBridge, times(1)).send("task-input-updated", taskDto);
        verify(stompBridge, times(2)).convertAndSend(anyString(), eq(taskDto));
        verify(stompBridge, never()).convertAndSend(contains("/events"), anyBoolean());
    }

    @Test
    void testNotifyWithEventsUpdate() {
        // Given
        final boolean withStatusUpdate = false;
        final boolean withEventsUpdate = true;
        final boolean withNewInput = false;

        // When
        taskUpdateNotifier.notify(task, withStatusUpdate, withEventsUpdate, withNewInput);

        // Then
        verify(streamBridge, never()).send(anyString(), any());
        verify(stompBridge, times(2)).convertAndSend(anyString(), eq(taskDto));
        verify(stompBridge, times(1)).convertAndSend(contains("/events"), eq(true));
    }

    @Test
    void testNotifyWithUpdates() {
        // Given
        final boolean withStatusUpdate = true;
        final boolean withEventsUpdate = true;
        final boolean withNewInput = true;

        // When
        taskUpdateNotifier.notify(task, withStatusUpdate, withEventsUpdate, withNewInput);

        // Then
        verify(streamBridge, times(1)).send("task-input-updated", taskDto);
        verify(streamBridge, times(1)).send("task-status-updated", taskDto);
        verify(stompBridge, times(2)).convertAndSend(anyString(), eq(taskDto));
        verify(stompBridge, times(1)).convertAndSend(contains("/events"), eq(true));
    }

    @Test
    void testNotifyWithoutUpdates() {
        // Given
        final boolean withStatusUpdate = false;
        final boolean withEventsUpdate = false;
        final boolean withNewInput = false;

        // When
        taskUpdateNotifier.notify(task, withStatusUpdate, withEventsUpdate, withNewInput);

        // Then
        verify(streamBridge, never()).send(anyString(), any());
        verify(stompBridge, times(2)).convertAndSend(anyString(), eq(taskDto));
        verify(stompBridge, never()).convertAndSend(contains("/events"), anyBoolean());
    }

}
