/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskLogEventUpdate;
import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.FileEventType;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessRun;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.entities.TaskWithStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessEventRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessFileRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@SpringBootTest
class TaskServiceTest {
    private static final String INPUT_FILE_GROUP_VALUE = MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE;

    @MockBean
    private TaskUpdateNotifier taskUpdateNotifier;
    @MockBean
    private StreamBridge streamBridge; // Useful to avoid AMQP connection that would fail
    @MockBean
    private MinioAdapter minioAdapter;
    @MockBean
    private ProcessFileRepository processFileRepository;
    @MockBean
    private TaskRepository taskRepository;
    @MockBean
    private ProcessEventRepository processEventRepository;

    @Autowired
    private TaskService taskService;

    ////////////////////////////
    // TASK STATUS MANAGEMENT //
    ////////////////////////////

    @Test
    void checkAndUpdateTaskStatusWithNoInputFile() {
        Task task = new Task();
        task.setStatus(TaskStatus.CREATED);

        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);

        boolean statusChanged = taskService.checkAndUpdateTaskStatus(task, false);

        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.NOT_CREATED);
        Assertions.assertThat(statusChanged).isTrue();
    }

    @Test
    void checkAndUpdateTaskStatusWithAllRequiredFilesAndSelectionChanged() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        task.setStatus(TaskStatus.CREATED);
        ProcessFile cgmFile = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        task.addProcessFile(cgmFile);
        ProcessFile cracFile = new ProcessFile("file2", "input", "CRAC", "documentIdCrac", timestamp, timestamp, timestamp);
        task.addProcessFile(cracFile);

        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);

        boolean statusChanged = taskService.checkAndUpdateTaskStatus(task, true);

        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
        Assertions.assertThat(statusChanged).isTrue();
    }

    @Test
    void checkAndUpdateTaskStatusWithAllRequiredFilesAndSelectionNotChanged() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        TaskStatus initialTaskStatus = TaskStatus.SUCCESS;
        task.setStatus(initialTaskStatus);
        ProcessFile cgmFile = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        task.addProcessFile(cgmFile);
        ProcessFile cracFile = new ProcessFile("file2", "input", "CRAC", "documentIdCrac", timestamp, timestamp, timestamp);
        task.addProcessFile(cracFile);

        boolean statusChanged = taskService.checkAndUpdateTaskStatus(task, false);

        Assertions.assertThat(task.getStatus()).isEqualTo(initialTaskStatus);
        Assertions.assertThat(statusChanged).isFalse();
    }

    @Test
    void checkAndUpdateTaskStatusWithNotAllRequiredFilesAndSelectionNotChanged() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        TaskStatus initialTaskStatus = TaskStatus.SUCCESS;
        task.setStatus(initialTaskStatus);
        ProcessFile cgmFile = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        task.addProcessFile(cgmFile);

        boolean statusChanged = taskService.checkAndUpdateTaskStatus(task, false);

        Assertions.assertThat(task.getStatus()).isEqualTo(initialTaskStatus);
        Assertions.assertThat(statusChanged).isFalse();
    }

    @Test
    void checkAndUpdateTaskStatusWithNotAllRequiredFilesAndSelectionChanged() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        TaskStatus initialTaskStatus = TaskStatus.SUCCESS;
        task.setStatus(initialTaskStatus);
        ProcessFile cgmFile = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        task.addProcessFile(cgmFile);

        boolean statusChanged = taskService.checkAndUpdateTaskStatus(task, true);

        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        Assertions.assertThat(statusChanged).isTrue();
    }

    //////////////////////////////
    // PROCESS EVENT MANAGEMENT //
    //////////////////////////////

    @Test
    void addProcessEventToTaskWithoutPrefix() {
        Task task = new Task();
        TaskLogEventUpdate eventUpdate = new TaskLogEventUpdate("event-id", "2024-05-16T09:30Z", "WARN", "The cake is a lie", "test-service");

        Assertions.assertThat(task.getProcessEvents()).isEmpty();

        taskService.addProcessEventToTask(eventUpdate, task);

        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(processEventRepository, times(1)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues()).hasSize(1);
        Assertions.assertThat(captor.getAllValues().get(0).getTimestamp()).hasToString("2024-05-16T09:30Z");
        Assertions.assertThat(captor.getAllValues().get(0).getLevel()).isEqualTo("WARN");
        Assertions.assertThat(captor.getAllValues().get(0).getMessage()).isEqualTo("The cake is a lie");
        Assertions.assertThat(captor.getAllValues().get(0).getServiceName()).isEqualTo("test-service");
    }

    @Test
    void addProcessEventToTaskWithPrefix() {
        Task task = new Task();
        TaskLogEventUpdate eventUpdate = new TaskLogEventUpdate("event-id", "2024-05-16T09:30Z", "WARN", "The cake is a lie", "test-service", "prefix");

        Assertions.assertThat(task.getProcessEvents()).isEmpty();

        taskService.addProcessEventToTask(eventUpdate, task);

        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(processEventRepository, times(1)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues()).hasSize(1);
        Assertions.assertThat(captor.getAllValues().get(0).getTimestamp()).hasToString("2024-05-16T09:30Z");
        Assertions.assertThat(captor.getAllValues().get(0).getLevel()).isEqualTo("WARN");
        Assertions.assertThat(captor.getAllValues().get(0).getMessage()).isEqualTo("[prefix] : The cake is a lie");
        Assertions.assertThat(captor.getAllValues().get(0).getServiceName()).isEqualTo("test-service");
    }

    @Test
    void addFileEventToTaskWhenManualUpload() {
        Task task = new Task(OffsetDateTime.now());
        ProcessFile processFile = new ProcessFile(
                "path/MANUAL_UPLOAD/to/cgm-file.xml",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        taskService.addFileEventToTask(task, FileEventType.UPDATED, processFile);
        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(processEventRepository, times(1)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues()).hasSize(1);
        Assertions.assertThat(captor.getAllValues().get(0).getMessage()).startsWith("Manual upload of a");
    }

    @Test
    void addFileEventToTaskWaiting() {
        Task task = new Task(OffsetDateTime.now());
        ProcessFile processFile = new ProcessFile(
                "path/to/cgm-file.xml",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        taskService.addFileEventToTask(task, FileEventType.WAITING, processFile);
        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(processEventRepository, times(1)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues()).hasSize(1);
        Assertions.assertThat(captor.getAllValues().get(0).getMessage()).startsWith("A new version of CGM is waiting for");
    }

    @Test
    void addFileEventToTaskUpdated() {
        Task task = new Task(OffsetDateTime.now());
        ProcessFile processFile = new ProcessFile(
                "path/to/cgm-file.xml",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        taskService.addFileEventToTask(task, FileEventType.UPDATED, processFile);
        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(processEventRepository, times(1)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues()).hasSize(1);
        Assertions.assertThat(captor.getAllValues().get(0).getMessage()).startsWith("A new version of CGM replaced");
    }

    @Test
    void addFileEventToTaskAvailable() {
        Task task = new Task(OffsetDateTime.now());
        ProcessFile processFile = new ProcessFile(
                "path/to/cgm-file.xml",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        taskService.addFileEventToTask(task, FileEventType.AVAILABLE, processFile);
        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(processEventRepository, times(1)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues()).hasSize(1);
        Assertions.assertThat(captor.getAllValues().get(0).getMessage()).startsWith("A new version of CGM is available");
    }

    @Test
    void addFileEventToTaskDefault() {
        Task task = new Task(OffsetDateTime.now());
        ProcessFile processFile = new ProcessFile(
                "path/to/cgm-file.xml",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        taskService.addFileEventToTask(task, FileEventType.DELETED, processFile);
        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(processEventRepository, times(1)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues()).hasSize(1);
        Assertions.assertThat(captor.getAllValues().get(0).getMessage()).startsWith("The CGM");
        Assertions.assertThat(captor.getAllValues().get(0).getMessage()).endsWith("is deleted");
    }

    @Test
    void addFileEventToTaskWithCustomLogLevel() {
        Task task = new Task(OffsetDateTime.now());
        ProcessFile processFile = new ProcessFile(
                "path/to/cgm-file.xml",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        taskService.addFileEventToTask(task, FileEventType.UPDATED, processFile, "ERROR");
        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(processEventRepository, times(1)).save(captor.capture());
        Assertions.assertThat(captor.getAllValues()).hasSize(1);
        Assertions.assertThat(captor.getAllValues().get(0).getLevel()).isEqualTo("ERROR");
    }

    //////////////////////////////
    // PROCESS FILES MANAGEMENT //
    //////////////////////////////

    @Test
    void addProcessFileToTasksWithOutputFileAndNoStatusUpdate() {
        OffsetDateTime timestamp01 = OffsetDateTime.parse("2021-10-11T01:00Z");
        OffsetDateTime timestamp02 = OffsetDateTime.parse("2021-10-11T02:00Z");
        OffsetDateTime timestamp03 = OffsetDateTime.parse("2021-10-11T03:00Z");
        ProcessFile processFile = new ProcessFile(
                "path/to/cne-file.xml",
                "output",
                "CNE",
                null,
                OffsetDateTime.parse("2021-10-11T01:00Z"),
                OffsetDateTime.parse("2021-10-11T04:00Z"),
                OffsetDateTime.parse("2021-10-11T00:18Z"));
        Task task01 = new Task(timestamp01);
        Task task02 = new Task(timestamp02);
        Task task03 = new Task(timestamp03);
        Mockito.when(taskRepository.findByTimestamp(timestamp01)).thenReturn(Optional.of(task01));
        Mockito.when(taskRepository.findByTimestamp(timestamp02)).thenReturn(Optional.of(task02));
        Mockito.when(taskRepository.findByTimestamp(timestamp03)).thenReturn(Optional.of(task03));

        Assertions.assertThat(task01.getOutput("CNE")).isEmpty();
        Assertions.assertThat(task02.getOutput("CNE")).isEmpty();
        Assertions.assertThat(task03.getOutput("CNE")).isEmpty();

        Set<TaskWithStatusUpdate> taskWithStatusUpdateSet = taskService.addProcessFileToTasks(processFile, FileEventType.UPDATED, false, false);

        Assertions.assertThat(taskWithStatusUpdateSet).isNotEmpty();
        Assertions.assertThat(task01.getOutput("CNE")).contains(processFile);
        Assertions.assertThat(task02.getOutput("CNE")).contains(processFile);
        Assertions.assertThat(task03.getOutput("CNE")).contains(processFile);
    }

    @Test
    void addProcessFileToTasksWithInputFileAndNoStatusUpdate() {
        OffsetDateTime timestamp01 = OffsetDateTime.parse("2021-10-11T01:00Z");
        OffsetDateTime timestamp02 = OffsetDateTime.parse("2021-10-11T02:00Z");
        ProcessFile processFileCgm = new ProcessFile(
                "path/to/cgm-file.xml",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T01:00Z"),
                OffsetDateTime.parse("2021-10-11T03:00Z"),
                OffsetDateTime.parse("2021-10-11T00:18Z"));
        ProcessFile processFileCracOld = new ProcessFile(
                "path/to/crac-file.xml",
                "input",
                "CRAC",
                "documentIdCrac",
                OffsetDateTime.parse("2021-10-11T01:00Z"),
                OffsetDateTime.parse("2021-10-11T03:00Z"),
                OffsetDateTime.parse("2021-10-11T00:18Z"));
        ProcessFile processFileCracNew = new ProcessFile(
                "path/to/crac-file.xml",
                "input",
                "CRAC",
                "documentIdCrac",
                OffsetDateTime.parse("2021-10-11T01:00Z"),
                OffsetDateTime.parse("2021-10-11T03:00Z"),
                OffsetDateTime.parse("2021-10-11T00:45Z"));
        Task task01 = new Task(timestamp01);
        ProcessRun processRun01 = new ProcessRun(List.of(processFileCgm));
        task01.addProcessFile(processFileCgm);
        task01.addProcessRun(processRun01);
        Task task02 = new Task(timestamp02);
        ProcessRun processRun02 = new ProcessRun(List.of(processFileCracOld));
        task02.addProcessFile(processFileCracOld);
        task02.addProcessRun(processRun02);
        Mockito.when(taskRepository.findByTimestamp(timestamp01)).thenReturn(Optional.of(task01));
        Mockito.when(taskRepository.findByTimestamp(timestamp02)).thenReturn(Optional.of(task02));

        Assertions.assertThat(task01.getInput("CGM")).contains(processFileCgm);
        Assertions.assertThat(task02.getInput("CRAC")).contains(processFileCracOld);

        Set<TaskWithStatusUpdate> taskWithStatusUpdateSet = taskService.addProcessFileToTasks(processFileCracNew, FileEventType.UPDATED, true, false);

        Assertions.assertThat(taskWithStatusUpdateSet).isNotEmpty();
        Assertions.assertThat(task01.getInput("CRAC")).contains(processFileCracNew);
        Assertions.assertThat(task01.getRunHistory().get(0).getInputFiles()).containsExactly(processFileCgm);
        Assertions.assertThat(task02.getInput("CRAC")).contains(processFileCracNew);
        Assertions.assertThat(task02.getRunHistory().get(0).getInputFiles()).isEmpty();
    }

    @Test
    void addProcessFileToTasksWithInputFileAndStatusUpdate() {
        OffsetDateTime timestamp01 = OffsetDateTime.parse("2021-10-11T01:00Z");
        OffsetDateTime timestamp02 = OffsetDateTime.parse("2021-10-11T02:00Z");
        ProcessFile processFileCgm = new ProcessFile(
                "path/to/cgm-file.xml",
                "input",
                "CGM",
                "documentIdCgm",
                OffsetDateTime.parse("2021-10-11T01:00Z"),
                OffsetDateTime.parse("2021-10-11T03:00Z"),
                OffsetDateTime.parse("2021-10-11T00:18Z"));
        ProcessFile processFileCracOld = new ProcessFile(
                "path/to/crac-file.xml",
                "input",
                "CRAC",
                "documentIdCrac",
                OffsetDateTime.parse("2021-10-11T01:00Z"),
                OffsetDateTime.parse("2021-10-11T03:00Z"),
                OffsetDateTime.parse("2021-10-11T00:18Z"));
        ProcessFile processFileCracNew = new ProcessFile(
                "path/to/crac-file.xml",
                "input",
                "CRAC",
                "documentIdCrac",
                OffsetDateTime.parse("2021-10-11T01:00Z"),
                OffsetDateTime.parse("2021-10-11T03:00Z"),
                OffsetDateTime.parse("2021-10-11T00:45Z"));
        Task task01 = new Task(timestamp01);
        ProcessRun processRun01 = new ProcessRun(List.of(processFileCgm));
        task01.addProcessFile(processFileCgm);
        task01.addProcessRun(processRun01);
        Task task02 = new Task(timestamp02);
        ProcessRun processRun02 = new ProcessRun(List.of(processFileCracOld));
        task02.addProcessFile(processFileCracOld);
        task02.addProcessRun(processRun02);
        Mockito.when(taskRepository.findByTimestamp(timestamp01)).thenReturn(Optional.of(task01));
        Mockito.when(taskRepository.findByTimestamp(timestamp02)).thenReturn(Optional.of(task02));

        Assertions.assertThat(task01.getInput("CGM")).contains(processFileCgm);
        Assertions.assertThat(task02.getInput("CRAC")).contains(processFileCracOld);

        Set<TaskWithStatusUpdate> taskWithStatusUpdateSet = taskService.addProcessFileToTasks(processFileCracNew, FileEventType.UPDATED, true, true);

        Assertions.assertThat(taskWithStatusUpdateSet).isNotEmpty();
        Assertions.assertThat(taskWithStatusUpdateSet)
                .filteredOn(twsu -> twsu.getTask().equals(task01))
                .first()
                .extracting("statusUpdated")
                .isEqualTo(true);
        Assertions.assertThat(taskWithStatusUpdateSet)
                .filteredOn(twsu -> twsu.getTask().equals(task02))
                .first()
                .extracting("statusUpdated")
                .isEqualTo(false);
    }

    @Test
    void removeProcessFileFromTasksWithInputFileAndNoStatusUpdate() {
        OffsetDateTime startingDate = OffsetDateTime.now();
        OffsetDateTime endingDate = startingDate.plusHours(3);
        ProcessFile processFileGlsk = new ProcessFile(
                "path/to/glsk-file.xml",
                "input",
                "GLSK",
                "documentIdGlsk",
                startingDate,
                endingDate,
                startingDate);
        ProcessFile processFileCrac = new ProcessFile(
                "path/to/crac-file.xml",
                "input",
                "CRAC",
                "documentIdCrac",
                startingDate,
                endingDate,
                startingDate);
        ProcessRun processRun = new ProcessRun(List.of(processFileCrac, processFileGlsk));
        Task task = new Task();
        task.addProcessFile(processFileGlsk);
        task.addProcessFile(processFileCrac);
        task.addProcessRun(processRun);
        task.setStatus(TaskStatus.CREATED);
        Mockito.when(taskRepository.findAllByTimestampBetween(startingDate, endingDate)).thenReturn(Set.of(task));

        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        Assertions.assertThat(task.getProcessFiles()).containsExactly(processFileCrac, processFileGlsk);
        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFileCrac, processFileGlsk);

        Set<TaskWithStatusUpdate> taskWithStatusUpdateSet = taskService.removeProcessFileFromTasks(processFileCrac);

        Assertions.assertThat(taskWithStatusUpdateSet).isNotNull();
        Assertions.assertThat(taskWithStatusUpdateSet).first().extracting("statusUpdated").isEqualTo(false);
        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        Mockito.verify(processEventRepository, Mockito.times(1)).save(any());
        Assertions.assertThat(task.getProcessFiles()).containsExactly(processFileGlsk);
        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFileGlsk);
    }

    @Test
    void removeProcessFileFromTasksWithInputFileAndStatusUpdate() {
        OffsetDateTime startingDate = OffsetDateTime.now();
        OffsetDateTime endingDate = startingDate.plusHours(3);
        ProcessFile processFileGlsk = new ProcessFile(
                "path/to/glsk-file.xml",
                "input",
                "GLSK",
                "documentIdGlsk",
                startingDate,
                endingDate,
                startingDate);
        ProcessFile processFileCrac = new ProcessFile(
                "path/to/crac-file.xml",
                "input",
                "CRAC",
                "documentIdCrac",
                startingDate,
                endingDate,
                startingDate);
        ProcessRun processRun = new ProcessRun(List.of(processFileCrac, processFileGlsk));
        Task task = new Task();
        task.addProcessFile(processFileGlsk);
        task.addProcessFile(processFileCrac);
        task.addProcessRun(processRun);
        task.setStatus(TaskStatus.READY);
        Mockito.when(taskRepository.findAllByTimestampBetween(startingDate, endingDate)).thenReturn(Set.of(task));

        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
        Assertions.assertThat(task.getProcessFiles()).containsExactly(processFileCrac, processFileGlsk);
        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFileCrac, processFileGlsk);

        Set<TaskWithStatusUpdate> taskWithStatusUpdateSet = taskService.removeProcessFileFromTasks(processFileCrac);

        Assertions.assertThat(taskWithStatusUpdateSet).isNotNull();
        Assertions.assertThat(taskWithStatusUpdateSet).first().extracting("statusUpdated").isEqualTo(true);
        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        Mockito.verify(processEventRepository, Mockito.times(1)).save(any());
        Assertions.assertThat(task.getProcessFiles()).containsExactly(processFileGlsk);
        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFileGlsk);
    }

    @Test
    void removeProcessFileFromTasksWithTaskReset() {
        OffsetDateTime startingDate = OffsetDateTime.now();
        OffsetDateTime endingDate = startingDate.plusHours(3);
        ProcessFile processFileCrac = new ProcessFile(
                "path/to/crac-file.xml",
                "input",
                "CRAC",
                "documentIdCrac",
                startingDate,
                endingDate,
                startingDate);
        ProcessRun processRun = new ProcessRun(List.of(processFileCrac));
        Task task = new Task();
        task.addProcessFile(processFileCrac);
        task.addProcessRun(processRun);
        taskService.addProcessEvent(task, startingDate, "INFO", "message", "serviceName");
        Mockito.when(taskRepository.findAllByTimestampBetween(startingDate, endingDate)).thenReturn(Set.of(task));
        Mockito.verify(processEventRepository, Mockito.times(1)).save(any());
        Assertions.assertThat(task.getProcessFiles()).containsExactly(processFileCrac);
        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFileCrac);

        Set<TaskWithStatusUpdate> taskWithStatusUpdateSet = taskService.removeProcessFileFromTasks(processFileCrac);

        Assertions.assertThat(taskWithStatusUpdateSet).isNotNull();
        Assertions.assertThat(task.getProcessEvents()).isEmpty();
        Assertions.assertThat(task.getProcessFiles()).isEmpty();
        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).isEmpty();
    }

//    @Test
    void removeProcessFileFromTasksWithOutputFile() {
        OffsetDateTime startingDate = OffsetDateTime.now();
        OffsetDateTime endingDate = startingDate.plusHours(3);
        ProcessFile processFileGlsk = new ProcessFile(
                "path/to/glsk-file.xml",
                "input",
                "GLSK",
                "documentIdGlsk",
                startingDate,
                endingDate,
                startingDate);
        ProcessFile processFileCne = new ProcessFile(
                "path/to/cne-file.xml",
                "input",
                "CNE",
                null,
                startingDate,
                endingDate,
                startingDate);
        ProcessRun processRun = new ProcessRun(List.of(processFileGlsk));
        Task task = new Task();
        task.addProcessFile(processFileGlsk);
        task.addProcessFile(processFileCne);
        task.addProcessRun(processRun);
        task.setStatus(TaskStatus.SUCCESS);
        Mockito.when(taskRepository.findAllByTimestampBetween(startingDate, endingDate)).thenReturn(Set.of(task));

        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        Assertions.assertThat(task.getProcessFiles()).containsExactly(processFileCne, processFileGlsk);
        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFileGlsk);
        //TODO WHY THIS NEW TEST FAILS ??
        Set<TaskWithStatusUpdate> taskWithStatusUpdateSet = taskService.removeProcessFileFromTasks(processFileCne);

        Assertions.assertThat(taskWithStatusUpdateSet).isNotNull();
        Assertions.assertThat(taskWithStatusUpdateSet).first().extracting("statusUpdated").isEqualTo(false);
        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        Mockito.verify(processEventRepository, Mockito.times(1)).save(any());
        Assertions.assertThat(task.getProcessFiles()).containsExactly(processFileGlsk);
        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFileGlsk);
    }

    @Test
    void selectFileThrowsFileNotFound() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.empty());

        Assertions.assertThatExceptionOfType(TaskNotFoundException.class)
                .isThrownBy(() -> taskService.selectFile(timestamp, "type", "name"));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"RUNNING", "PENDING", "STOPPING"})
    void selectFileThrowsStatusBlockFileSelection(TaskStatus taskStatus) {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        task.setStatus(taskStatus);
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.of(task));

        Assertions.assertThatExceptionOfType(TaskManagerException.class)
                .isThrownBy(() -> taskService.selectFile(timestamp, "type", "name"))
                .withMessage("Status of task does not allow to change selected file");
    }

    @Test
    void selectFileThrowsProcessFileNotFound() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        task.setStatus(TaskStatus.READY);
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.of(task));

        Assertions.assertThatExceptionOfType(ProcessFileNotFoundException.class)
                .isThrownBy(() -> taskService.selectFile(timestamp, "type", "name"));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"READY", "SUCCESS", "ERROR", "INTERRUPTED"})
    void selectFile(TaskStatus taskStatus) {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        task.setStatus(taskStatus);
        ProcessFile processFile1 = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        task.addProcessFile(processFile1);
        ProcessFile processFile2 = new ProcessFile("file2", "input", "CRAC", "documentIdCrac", timestamp, timestamp, timestamp);
        task.addProcessFile(processFile2);
        ProcessFile processFile3 = new ProcessFile("file3", "input", "CRAC", "documentIdCrac", timestamp, timestamp, timestamp);
        task.addProcessFile(processFile3);
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.of(task));

        Assertions.assertThat(task.getInput("CRAC")).contains(processFile3);
        Assertions.assertThat(task.getStatus()).isEqualTo(taskStatus);

        taskService.selectFile(timestamp, "CRAC", "file2");

        Assertions.assertThat(task.getInput("CRAC")).contains(processFile2);
        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
    }

    ////////////////////////////
    // RUN HISTORY MANAGEMENT //
    ////////////////////////////

    @Test
    void addNewRunAndSaveTaskThrowsTaskNotFound() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.empty());

        List<ProcessFileDto> inputFileDtos = List.of();
        Assertions.assertThatExceptionOfType(TaskNotFoundException.class)
                .isThrownBy(() -> taskService.addNewRunAndSaveTask(timestamp, inputFileDtos));
    }

    @Test
    void addNewRunAndSaveTaskThrowsProcessFileNotFound() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        ProcessFile processFile = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        task.addProcessFile(processFile);
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.of(task));

        ProcessFileDto processFileDto = new ProcessFileDto("path/to/file", "CRAC", ProcessFileStatus.VALIDATED, "file2", "documentIdCrac", timestamp);
        List<ProcessFileDto> inputFileDtos = List.of(processFileDto);
        Assertions.assertThatExceptionOfType(ProcessFileNotFoundException.class)
                .isThrownBy(() -> taskService.addNewRunAndSaveTask(timestamp, inputFileDtos));
    }

    @Test
    void addNewRunAndSaveTaskOk() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        ProcessFile processFile1 = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        task.addProcessFile(processFile1);
        ProcessFile processFile2 = new ProcessFile("file2", "input", "CRAC", "documentIdCrac", timestamp, timestamp, timestamp);
        task.addProcessFile(processFile2);
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.of(task));
        Mockito.when(taskRepository.save(task)).thenReturn(task);

        Assertions.assertThat(task.getRunHistory()).isEmpty();

        ProcessFileDto processFileDto1 = new ProcessFileDto("path/to/file1", "CGM", ProcessFileStatus.VALIDATED, "file1", "documentIdCgm", timestamp);
        ProcessFileDto processFileDto2 = new ProcessFileDto("path/to/file2", "CRAC", ProcessFileStatus.VALIDATED, "file2", "documentIdCrac", timestamp);
        List<ProcessFileDto> inputFileDtos = List.of(processFileDto1, processFileDto2);

        Task savedTask = taskService.addNewRunAndSaveTask(timestamp, inputFileDtos);

        Assertions.assertThat(savedTask.getRunHistory()).hasSize(1);
        Assertions.assertThat(savedTask.getRunHistory().get(0).getInputFiles()).containsExactly(processFile1, processFile2);
    }

    @Test
    void addNewRunAndSaveTaskOkWithAvailableInputFilesNotInitializedCorrectly() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = Mockito.mock(Task.class);
        ProcessFile processFile1 = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        Mockito.when(task.getInput("CGM")).thenReturn(Optional.of(processFile1));
        Mockito.when(task.getAvailableInputs("CGM")).thenReturn(Set.of(processFile1));
        ProcessFile processFile2 = new ProcessFile("file2", "input", "CRAC", "documentIdCrac", timestamp, timestamp, timestamp);
        Mockito.when(task.getInput("CRAC")).thenReturn(Optional.of(processFile2));
        Mockito.when(task.getAvailableInputs("CRAC")).thenReturn(Set.of());
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.of(task));
        Mockito.when(taskRepository.save(task)).thenReturn(task);

        ProcessFileDto processFileDto1 = new ProcessFileDto("path/to/file1", "CGM", ProcessFileStatus.VALIDATED, "file1", "documentIdCgm", timestamp);
        ProcessFileDto processFileDto2 = new ProcessFileDto("path/to/file2", "CRAC", ProcessFileStatus.VALIDATED, "file2", "documentIdCrac", timestamp);
        List<ProcessFileDto> inputFileDtos = List.of(processFileDto1, processFileDto2);

        taskService.addNewRunAndSaveTask(timestamp, inputFileDtos);

        ArgumentCaptor<ProcessRun> captor = ArgumentCaptor.forClass(ProcessRun.class);
        Mockito.verify(task, Mockito.times(1)).addProcessRun(captor.capture());

        Assertions.assertThat(captor.getValue().getInputFiles()).containsExactly(processFile1, processFile2);
    }

    @Test
    void addNewRunAndSaveTaskTestWithOptionalEntries() {
        OffsetDateTime timestamp = OffsetDateTime.now();
        Task task = new Task();
        ProcessFile processFile1 = new ProcessFile("file1", "input", "CGM", "documentIdCgm", timestamp, timestamp, timestamp);
        task.addProcessFile(processFile1);
        ProcessFile processFile2 = new ProcessFile("file2", "input", "OPTIONAL_INPUT", "documentIdCrac", timestamp, timestamp, timestamp);
        task.addProcessFile(processFile2);
        Mockito.when(taskRepository.findByTimestamp(any())).thenReturn(Optional.of(task));
        Mockito.when(taskRepository.save(task)).thenReturn(task);

        Assertions.assertThat(task.getRunHistory()).isEmpty();

        ProcessFileDto processFileDto1 = new ProcessFileDto("path/to/file1", "CGM", ProcessFileStatus.VALIDATED, "file1", "documentIdCgm", timestamp);
        ProcessFileDto processFileDto2 = new ProcessFileDto("path/to/file2", "OPTIONAL_INPUT", ProcessFileStatus.NOT_PRESENT, "file2", "documentIdCrac", timestamp);
        List<ProcessFileDto> inputFileDtos = List.of(processFileDto1, processFileDto2);

        Task savedTask = taskService.addNewRunAndSaveTask(timestamp, inputFileDtos);

        Assertions.assertThat(savedTask.getRunHistory()).hasSize(1);
        Assertions.assertThat(savedTask.getRunHistory().get(0).getInputFiles()).containsExactly(processFile1);
    }

    @ParameterizedTest
    @EnumSource(value = FileEventType.class, names = {"AVAILABLE", "WAITING"})
    void removeUnavailableProcessFileFromTaskRunHistoryWithBadFileEventType(FileEventType fileEventType) {
        ProcessFile processFile1 = new ProcessFile("path/to/file-name", "input", "CGM", "documentIdCgm", OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
        ProcessFile processFile2 = new ProcessFile("path/to/other-file-name", "input", "GLSK", "documentIdGlsk", OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
        ProcessRun processRun = new ProcessRun(List.of(processFile1, processFile2));
        Task task = new Task(OffsetDateTime.now());
        task.addProcessFile(processFile1);
        task.addProcessFile(processFile2);
        task.addProcessRun(processRun);

        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFile1, processFile2);

        TaskService.removeUnavailableProcessFileFromTaskRunHistory(processFile1, task, fileEventType);

        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFile1, processFile2);
    }

    @ParameterizedTest
    @EnumSource(value = FileEventType.class, names = {"UPDATED", "DELETED"})
    void removeUnavailableProcessFileFromTaskRunHistoryWithGoodFileEventType(FileEventType fileEventType) {
        ProcessFile processFile1 = new ProcessFile("path/to/file-name", "input", "CGM", "documentIdCgm", OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
        ProcessFile processFile2 = new ProcessFile("path/to/other-file-name", "input", "GLSK", "documentIdGlsk", OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
        ProcessRun processRun = new ProcessRun(List.of(processFile1, processFile2));
        Task task = new Task(OffsetDateTime.now());
        task.addProcessFile(processFile1);
        task.addProcessFile(processFile2);
        task.addProcessRun(processRun);

        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFile1, processFile2);

        TaskService.removeUnavailableProcessFileFromTaskRunHistory(processFile1, task, fileEventType);

        Assertions.assertThat(task.getRunHistory().get(0).getInputFiles()).containsExactly(processFile2);
    }
}
