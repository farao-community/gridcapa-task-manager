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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@SpringBootTest
class ProcessEventTest {

    @Autowired
    private TaskDtoBuilder taskDtoBuilder;

    @Test
    void testConstructorFromEntity() {
        ProcessEvent processEvent = new ProcessEvent(null, OffsetDateTime.now(), "INFO", "CGM arrived", "serviceName");
        ProcessEventDto processEventDto = taskDtoBuilder.createDtoFromEntity(processEvent);
        assertEquals("INFO", processEventDto.getLevel());
        assertNotNull(processEvent.getTimestamp());
        assertEquals("CGM arrived", processEventDto.getMessage());
    }
}
