/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessEventDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class ProcessEventTest {

    @Test
    void testConstructorFromEntity() {
        Task task = Mockito.mock(Task.class);
        ProcessEvent processEvent = new ProcessEvent(task, OffsetDateTime.now(), "INFO", "CGM arrived");
        ProcessEventDto processEventDto = ProcessEvent.createDtoFromEntity(processEvent);
        assertEquals("INFO", processEventDto.getLevel());
        assertNotNull(processEvent.getTimestamp());
        assertEquals("CGM arrived", processEventDto.getMessage());
    }
}
