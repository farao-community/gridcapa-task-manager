/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.entities;

import com.farao_community.farao.gridcapa.task_manager.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskTest {

    @Autowired
    TaskRepository taskRepository;

    @Test
    void testTaskCreation() {
        LocalDateTime timestamp = LocalDateTime.parse("2021-10-11T10:18");
        Task task = new Task(timestamp, List.of("CGM", "CRAC"));
        taskRepository.save(task);

        Optional<Task> optSavedTask = taskRepository.findByTimestamp(timestamp);
        assertTrue(optSavedTask.isPresent());
        Task savedTask = optSavedTask.get();
        assertEquals(timestamp, savedTask.getTimestamp());
        assertEquals(TaskStatus.CREATED, savedTask.getStatus());
        assertEquals(2, savedTask.getProcessFiles().size());

        ProcessFile cgmFile = savedTask.getProcessFile("CGM");
        assertEquals("CGM", cgmFile.getFileType());
        assertEquals(ProcessFileStatus.NOT_PRESENT, cgmFile.getProcessFileStatus());

        assertThrows(RuntimeException.class, () -> savedTask.getProcessFile("GLSK"));
    }
}
