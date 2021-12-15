/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
class TaskDtoTest {

    @Test
    void testConstructorEmptyProcessFile() {
        LocalDateTime timestamp = LocalDateTime.parse("2021-10-11T10:18");
        TaskDto taskDto = TaskDto.emptyTask(timestamp, List.of("CGM", "CRAC"));
        assertEquals(timestamp, taskDto.getTimestamp());
        assertEquals(TaskStatus.NOT_CREATED, taskDto.getStatus());
        assertEquals(2, taskDto.getProcessFiles().size());
        assertEquals(0, taskDto.getProcessEvents().size());
    }
}
