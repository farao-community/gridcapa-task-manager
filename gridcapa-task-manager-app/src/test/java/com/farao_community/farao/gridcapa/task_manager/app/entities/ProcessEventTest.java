/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessEventDto;
import com.farao_community.farao.gridcapa.task_manager.app.TaskDtoBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@SpringBootTest
class ProcessEventTest {

    @Autowired
    private TaskDtoBuilder taskDtoBuilder;

    @Test
    void testConstructorFromEntity() {
        Task task = new Task(OffsetDateTime.parse("2021-01-01T00:00Z"));
        ProcessEvent processEvent = new ProcessEvent(task, OffsetDateTime.now(), "INFO", "CGM arrived");
        ProcessEventDto processEventDto = taskDtoBuilder.createDtoFromEntity(processEvent);
        assertEquals("INFO", processEventDto.getLevel());
        assertNotNull(processEvent.getTimestamp());
        assertEquals("CGM arrived", processEventDto.getMessage());
        assertEquals(task, processEvent.getTask());
    }

    @Test
    void testEquals() {
        Task task1 = new Task(OffsetDateTime.parse("2021-01-01T00:00Z"));
        ProcessEvent processEvent1 = new ProcessEvent(task1, OffsetDateTime.now(), "INFO", "CGM arrived");
        Task task2 = new Task(OffsetDateTime.parse("2021-01-01T00:00Z"));
        ProcessEvent processEvent2 = new ProcessEvent(task2, OffsetDateTime.now(), "INFO", "CGM arrived");
        ProcessEvent processEvent3 = new ProcessEvent(task1, OffsetDateTime.now(), "INFO", "CGM arrived");
        assertFalse(processEvent1.equals(processEvent2));
        assertFalse(processEvent2.equals(processEvent1));
        assertFalse(processEvent2.equals(task2));
        assertFalse(processEvent2.equals(null));
        assertTrue(processEvent1.equals(processEvent1));
        assertTrue(processEvent2.equals(processEvent2));
        assertFalse(processEvent1.equals(processEvent3));
    }
}
