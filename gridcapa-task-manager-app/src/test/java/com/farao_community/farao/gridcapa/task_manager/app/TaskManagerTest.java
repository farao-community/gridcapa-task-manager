/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import io.minio.messages.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.CREATED;
import static com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.READY;
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
                TaskManager.FILE_PROCESS_TAG, processTag,
                TaskManager.FILE_TYPE, fileType,
                TaskManager.FILE_VALIDITY_INTERVAL, validityInterval
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

    @Test
    void testCreationEventsForTwoFilesWithDifferentTypesAndSameTs() {
        LocalDateTime taskTimestamp = LocalDateTime.parse("2021-09-30T21:00");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        String cracUrl = "cracUrl";
        Event eventCrac = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T23:00/2021-10-01T00:00", cracUrl);

        taskManager.updateTasks(eventCgm);
        taskManager.updateTasks(eventCrac);

        Task task = taskRepository.findByTimestamp(taskTimestamp).get();
        assertEquals(2, task.getProcessEvents().size());
        assertEquals("INFO", task.getProcessEvents().get(0).getLevel());
        assertEquals("INFO", task.getProcessEvents().get(1).getLevel());
        assertEquals("The CGM : 'cgm-test' is available", task.getProcessEvents().get(0).getMessage());
        assertEquals("The CRAC : 'crac-test' is available", task.getProcessEvents().get(1).getMessage());
    }

    @Test
    void testUpdateEventsForTwoFilesWithSameTypeAndSameTs() {
        LocalDateTime taskTimestamp = LocalDateTime.parse("2021-09-30T21:00");
        String cgmUrl = "cgmUrl";
        Event eventCgm = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-test", "2021-09-30T23:00/2021-10-01T00:00", cgmUrl);

        String cgmUrlNew = "cgmUrlNew";
        Event eventCgmNew = createEvent("CSE_D2CC", "CGM", "CSE/D2CC/CGMs/cgm-new-test", "2021-09-30T23:00/2021-10-01T00:00", cgmUrlNew);

        taskManager.updateTasks(eventCgm);
        taskManager.updateTasks(eventCgmNew);

        Task task = taskRepository.findByTimestamp(taskTimestamp).get();
        assertEquals(2, task.getProcessEvents().size());
        assertTrue(task.getProcessEvents().get(0).getTimestamp().isBefore(task.getProcessEvents().get(1).getTimestamp()));
        assertEquals("INFO", task.getProcessEvents().get(0).getLevel());
        assertEquals("INFO", task.getProcessEvents().get(1).getLevel());
        assertEquals("The CGM : 'cgm-test' is available", task.getProcessEvents().get(0).getMessage());
        assertEquals("A new version of CGM  is available : 'cgm-new-test'", task.getProcessEvents().get(1).getMessage());
    }

    @Test
    void testDeletionEventsOfTaskWithTwoDifferentFileTypes() {
        LocalDateTime taskTimestamp = LocalDateTime.parse("2021-10-01T21:00");
        Task task = new Task(taskTimestamp, Arrays.asList("CGM", "CRAC"));
        ProcessFile processFileCgm = task.getProcessFile("CGM");
        processFileCgm.setFileUrl("cgmUrl");
        processFileCgm.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        processFileCgm.setLastModificationDate(LocalDateTime.now());
        processFileCgm.setFileObjectKey("CSE/D2CC/CGMs/cgm-test");
        processFileCgm.setFilename("cgm-test");
        ProcessEvent eventCgm = new ProcessEvent(task, LocalDateTime.now(), "INFO", "CGM available");
        task.getProcessEvents().add(eventCgm);

        ProcessFile processFileCrac = task.getProcessFile("CRAC");
        processFileCrac.setFileUrl("cracUrl");
        processFileCrac.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        processFileCrac.setLastModificationDate(LocalDateTime.now());
        processFileCrac.setFileObjectKey("CSE/D2CC/CRACs/crac-test");
        processFileCrac.setFilename("crac-test");
        ProcessEvent eventCrac = new ProcessEvent(task, LocalDateTime.now(), "INFO", "Crac available");
        task.getProcessEvents().add(eventCrac);

        taskRepository.save(task);

        Event eventCracDeletion = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T23:00/2021-10-01T00:00", "cracUrl");
        taskManager.removeProcessFile(eventCracDeletion);

        Task updatedTask = taskRepository.findByTimestamp(taskTimestamp).get();
        assertEquals(3, updatedTask.getProcessEvents().size());

        assertEquals("The CRAC : 'crac-test' is deleted", updatedTask.getProcessEvents().get(2).getMessage());
    }

    @Test
    void testDeletionEventsWithTaskDeletion() {
        LocalDateTime taskTimestamp = LocalDateTime.parse("2021-10-01T21:00");
        Task task = new Task(taskTimestamp, List.of("CRAC"));

        ProcessFile processFileCrac = task.getProcessFile("CRAC");
        processFileCrac.setFileUrl("cracUrl");
        processFileCrac.setProcessFileStatus(ProcessFileStatus.VALIDATED);
        processFileCrac.setLastModificationDate(LocalDateTime.now());
        processFileCrac.setFileObjectKey("CSE/D2CC/CRACs/crac-test");
        processFileCrac.setFilename("crac-test");
        ProcessEvent eventCrac = new ProcessEvent(task, LocalDateTime.now(), "INFO", "Crac available");
        task.getProcessEvents().add(eventCrac);

        taskRepository.save(task);

        Event eventCracDeletion = createEvent("CSE_D2CC", "CRAC", "CSE/D2CC/CRACs/crac-test", "2021-09-30T23:00/2021-10-01T00:00", "cracUrl");
        taskManager.removeProcessFile(eventCracDeletion);

        assertTrue(taskRepository.findByTimestamp(taskTimestamp).isEmpty());
    }
}
