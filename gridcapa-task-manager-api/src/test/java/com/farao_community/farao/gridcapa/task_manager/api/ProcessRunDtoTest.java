/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class ProcessRunDtoTest {

    @Test
    void testConstructor() {
        ProcessFileDto processFileDto = new ProcessFileDto(null, null, null, null, null);
        OffsetDateTime now = OffsetDateTime.now();
        List<ProcessFileDto> inputFiles = List.of(processFileDto);
        ProcessRunDto processRunDto = new ProcessRunDto(now, inputFiles);
        assertEquals(now, processRunDto.getExecutionDate());
        assertEquals(inputFiles, processRunDto.getInputs());
    }
}
