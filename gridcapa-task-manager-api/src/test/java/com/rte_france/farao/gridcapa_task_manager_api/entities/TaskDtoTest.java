/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.farao.gridcapa_task_manager_api.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
class TaskDtoTest {

    @Test
    void testConstructorFromEntity() {
        LocalDateTime timestamp = LocalDateTime.parse("2021-10-11T10:18");
        Task task = new Task(timestamp, List.of("CGM", "CRAC"));
        TaskDto taskDto = TaskDto.fromEntity(task);
        assertEquals(timestamp, taskDto.getTimestamp());
        assertEquals(TaskStatus.CREATED, taskDto.getStatus());
        assertEquals(2, taskDto.getProcessFiles().size());
    }

    @Test
    void testConstructorEmptyProcessFile() {
        LocalDateTime timestamp = LocalDateTime.parse("2021-10-11T10:18");
        TaskDto taskDto = TaskDto.emptyTask(timestamp, List.of("CGM", "CRAC"));
        assertEquals(timestamp, taskDto.getTimestamp());
        assertEquals(TaskStatus.NOT_CREATED, taskDto.getStatus());
        assertEquals(2, taskDto.getProcessFiles().size());
    }
}
