/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
class TaskDtoTest {

    @Test
    void testConstructorEmptyProcessFile() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:18Z");

        TaskDto taskDto = TaskDto.emptyTask(timestamp, List.of("CGM", "CRAC"), List.of("CNE"));

        assertEquals(timestamp, taskDto.getTimestamp());
        assertEquals(TaskStatus.NOT_CREATED, taskDto.getStatus());
        assertEquals(2, taskDto.getInputs().size());
        assertEquals(1, taskDto.getOutputs().size());
        assertEquals(0, taskDto.getAvailableInputs().size());
        assertEquals(0, taskDto.getRunHistory().size());
        assertEquals(0, taskDto.getProcessEvents().size());
    }

    @Test
    void testConstructor() {
        ProcessFileDto file1 = new ProcessFileDto("file1", null, null, "file1", null);
        ProcessFileDto file2 = new ProcessFileDto("file2", null, null, "file2", null);
        ProcessFileDto file3 = new ProcessFileDto("file3", null, null, "file3", null);
        ProcessFileDto file4 = new ProcessFileDto("file4", null, null, "file4", null);
        ProcessEventDto event1 = new ProcessEventDto(null, null, "event1", null);
        ProcessEventDto event2 = new ProcessEventDto(null, null, "event2", null);
        ProcessRunDto run = new ProcessRunDto(null, null);
        TaskParameterDto parameter = new TaskParameterDto(null, null, null, null);
        UUID id = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.parse("2021-10-11T10:18Z");
        TaskStatus status = TaskStatus.INTERRUPTED;
        List<ProcessFileDto> inputs = List.of(file1);
        List<ProcessFileDto> availableInputs = List.of(file1, file2);
        List<ProcessFileDto> outputs = List.of(file3, file4);
        List<ProcessEventDto> events = List.of(event1, event2);
        List<ProcessRunDto> runHistory = List.of(run);
        List<TaskParameterDto> parameters = List.of(parameter);

        TaskDto taskDto = new TaskDto(id, timestamp, status, inputs, availableInputs, outputs, events, runHistory, parameters);

        assertEquals(id, taskDto.getId());
        assertEquals(timestamp, taskDto.getTimestamp());
        assertEquals(status, taskDto.getStatus());
        assertEquals(1, taskDto.getInputs().size());
        assertTrue(taskDto.getInputs().contains(file1));
        assertEquals(2, taskDto.getAvailableInputs().size());
        assertTrue(taskDto.getAvailableInputs().containsAll(List.of(file1, file2)));
        assertEquals(2, taskDto.getOutputs().size());
        assertTrue(taskDto.getOutputs().containsAll(List.of(file3, file4)));
        assertEquals(2, taskDto.getProcessEvents().size());
        assertTrue(taskDto.getProcessEvents().containsAll(List.of(event1, event2)));
        assertEquals(1, taskDto.getRunHistory().size());
        assertTrue(taskDto.getRunHistory().contains(run));
        assertEquals(1, taskDto.getParameters().size());
        assertTrue(taskDto.getParameters().contains(parameter));
    }
}
