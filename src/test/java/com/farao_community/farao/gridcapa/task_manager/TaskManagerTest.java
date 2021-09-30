/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager;

import io.minio.messages.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static com.farao_community.farao.gridcapa.task_manager.TaskManager.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskManagerTest {

    @MockBean
    MinioAdapter minioAdapter;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskManager taskManager;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
    }

    private Event createEvent(String processTag, String fileType, String validityInterval, String fileUrl) {
        Event event = Mockito.mock(Event.class);
        Map<String, String> metadata = Map.of(
                FILE_PROCESS_TAG, processTag,
                FILE_TYPE, fileType,
                FILE_VALIDITY_INTERVAL, validityInterval
        );
        Mockito.when(event.userMetadata()).thenReturn(metadata);
        Mockito.when(minioAdapter.generatePreSignedUrl(event)).thenReturn(fileUrl);
        return event;
    }

    @Test
    void testUpdate() {
        String taskTimestamp = "2021-09-30T23:00+02:00";
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "CGM", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        taskManager.updateTasks(event);

        assertTrue(taskRepository.existsById(taskTimestamp));
        assertEquals(cgmUrl, taskRepository.findById(taskTimestamp).get().getFileTypeToUrlMap().get("CGM"));
    }

    @Test
    void testUpdateWithTwoFileTypesInTheSameTimestamp() {
        String taskTimestamp = "2021-09-30T23:00+02:00";
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = createEvent("CSE_D2CC", "CRAC", "2021-09-30T23:00/2021-10-01T00:00", cracUrl);

        taskManager.updateTasks(eventCgm);
        taskManager.updateTasks(eventCrac);

        assertEquals(1, taskRepository.findAll().size());
        assertTrue(taskRepository.existsById(taskTimestamp));
        assertEquals(cgmUrl, taskRepository.findById(taskTimestamp).get().getFileTypeToUrlMap().get("CGM"));
        assertEquals(cracUrl, taskRepository.findById(taskTimestamp).get().getFileTypeToUrlMap().get("CRAC"));
    }

    @Test
    void testUpdateWithNotHandledProcess() {
        String taskTimestamp = "2021-09-30T23:00+02:00";
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_IDCC", "CGM", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        taskManager.updateTasks(event);

        assertFalse(taskRepository.existsById(taskTimestamp));
    }

    @Test
    void testUpdateWithNotHandledFileType() {
        String taskTimestamp = "2021-09-30T23:00+02:00";
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "GLSK", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        taskManager.updateTasks(event);

        assertFalse(taskRepository.existsById(taskTimestamp));
    }

    @Test
    void testUpdateWithDailyFile() {
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "CGM", "2021-09-30T00:00/2021-10-01T00:00", cgmUrl);

        taskManager.updateTasks(event);

        assertEquals(24, taskRepository.findAll().size());
    }
}
