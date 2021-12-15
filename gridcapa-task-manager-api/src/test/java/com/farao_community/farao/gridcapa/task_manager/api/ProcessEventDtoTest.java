/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class ProcessEventDtoTest {

    @Test
    void testConstructorEmptyProcessFile() {
        ProcessEventDto processEventDto = new ProcessEventDto(LocalDateTime.now(), "INFO","CGM created");
        assertNotNull(processEventDto.getTimestamp());
        assertEquals("INFO", processEventDto.getLevel());
        assertEquals("CGM created", processEventDto.getMessage());
    }
}
