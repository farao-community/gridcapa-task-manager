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

import java.time.LocalDateTime;
import java.util.Map;

import static com.farao_community.farao.gridcapa.task_manager.TaskManager.*;
import static com.farao_community.farao.gridcapa.task_manager.entities.TaskStatus.CREATED;
import static com.farao_community.farao.gridcapa.task_manager.entities.TaskStatus.READY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskManagerTest {

    @MockBean
    private TaskNotifier taskNotifier; // Useful to avoid AMQP connection that would fail

    @MockBean
    private MinioAdapter minioAdapter;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskManager taskManager;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
    }

    private Event createEvent(String processTag, String fileType, String fileKey, String validityInterval, String fileUrl) {
        Event event = Mockito.mock(Event.class);
        Map<String, String> metadata = Map.of(
                FILE_PROCESS_TAG, processTag,
                FILE_TYPE, fileType,
                FILE_VALIDITY_INTERVAL, validityInterval
        );
        Mockito.when(event.userMetadata()).thenReturn(metadata);
        Mockito.when(minioAdapter.generatePreSignedUrl(event)).thenReturn(fileUrl);
        Mockito.when(event.objectName()).thenReturn(fileKey);
        return event;
    }

    @Test
    void testUpdate() {
        LocalDateTime taskTimestamp = LocalDateTime.parse("2021-09-30T21:00");
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        taskManager.updateTasks(event);

        assertTrue(taskRepository.existsByTimestamp(taskTimestamp));
        assertEquals(cgmUrl, taskRepository.findByTimestamp(taskTimestamp).get().getProcessFile("CGM").getFileUrl());
        assertEquals("cgm-test", taskRepository.findByTimestamp(taskTimestamp).get().getProcessFile("CGM").getFilename());
    }

    @Test
    void testUpdateWithTwoFileTypesInTheSameTimestamp() {
        LocalDateTime taskTimestamp = LocalDateTime.parse("2021-09-30T21:00");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T23:00/2021-10-01T00:00", cracUrl);

        taskManager.updateTasks(eventCgm);
        taskManager.updateTasks(eventCrac);

        assertEquals(1, taskRepository.findAll().size());
        assertTrue(taskRepository.existsByTimestamp(taskTimestamp));
        assertEquals(cgmUrl, taskRepository.findByTimestamp(taskTimestamp).get().getProcessFile("CGM").getFileUrl());
        assertEquals(cracUrl, taskRepository.findByTimestamp(taskTimestamp).get().getProcessFile("CRAC").getFileUrl());
        assertEquals("cgm-test", taskRepository.findByTimestamp(taskTimestamp).get().getProcessFile("CGM").getFilename());
        assertEquals("crac-test", taskRepository.findByTimestamp(taskTimestamp).get().getProcessFile("CRAC").getFilename());
    }

    @Test
    void testUpdateWithNotHandledProcess() {
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_IDCC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        taskManager.updateTasks(event);

        assertEquals(0, taskRepository.findAll().size());
    }

    @Test
    void testUpdateWithNotHandledFileType() {
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "GLSK", "CSE/D2CC/GLSKs/glsk-test", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        taskManager.updateTasks(event);

        assertEquals(0, taskRepository.findAll().size());
    }

    @Test
    void testUpdateWithDailyFile() {
        String cgmUrl = "cgmUrl";
        Event event = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T00:00/2021-10-01T00:00", cgmUrl);

        taskManager.updateTasks(event);

        assertEquals(24, taskRepository.findAll().size());
    }

    @Test
    void checkStatusUpdateToReady() {
        LocalDateTime taskTimestamp = LocalDateTime.parse("2021-09-30T21:00");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T23:00/2021-10-01T00:00", cracUrl);

        taskManager.updateTasks(eventCgm);
        assertEquals(CREATED, taskRepository.findByTimestamp(taskTimestamp).get().getStatus());
        taskManager.updateTasks(eventCrac);
        assertEquals(READY, taskRepository.findByTimestamp(taskTimestamp).get().getStatus());
    }
}
