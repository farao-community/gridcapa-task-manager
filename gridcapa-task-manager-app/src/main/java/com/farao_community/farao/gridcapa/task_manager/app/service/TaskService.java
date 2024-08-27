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
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.FileEventType;
import com.farao_community.farao.gridcapa.task_manager.app.entities.FileRemovalStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessRun;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.entities.TaskWithStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessEventRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Service
public class TaskService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskService.class);
    private static final String FILE_EVENT_DEFAULT_LEVEL = "INFO";

    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final ProcessEventRepository processEventRepository;

    @Value("${spring.application.name}")
    private String serviceName;

    public TaskService(TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                       TaskRepository taskRepository,
                       ProcessEventRepository processEventRepository) {
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskRepository = taskRepository;
        this.processEventRepository = processEventRepository;
    }

    ////////////////////////////
    // TASK STATUS MANAGEMENT //
    ////////////////////////////

    /**
     * We compare the size of inputs list from process files of the task and the size of inputs from configuration.
     * If its equal task is ready otherwise it is created. When it is null it is not created.
     * This works because we consider there are only one file type per inputs. We call this method at adding and
     * deletion to check if the status has changed.
     *
     * @param task:                      Task on which to evaluate the status.
     * @param inputFileSelectionChanged: boolean indicating whether an input file has been changed
     */
    boolean checkAndUpdateTaskStatus(Task task, boolean inputFileSelectionChanged) {
        TaskStatus initialTaskStatus = task.getStatus();
        List<String> inputFileTypes = task.getProcessFiles().stream()
                .filter(ProcessFile::isInputFile)
                .map(ProcessFile::getFileType)
                .toList();
        if (inputFileTypes.isEmpty()) {
            task.setStatus(TaskStatus.NOT_CREATED);
        } else if (inputFileSelectionChanged && new HashSet<>(inputFileTypes).containsAll(taskManagerConfigurationProperties.getProcess().getInputs())) {
            task.setStatus(TaskStatus.READY);
        } else if (inputFileSelectionChanged) {
            task.setStatus(TaskStatus.CREATED);
        }
        return initialTaskStatus != task.getStatus();
    }

    //////////////////////////////
    // PROCESS EVENT MANAGEMENT //
    //////////////////////////////

    public void addProcessEventToTask(TaskLogEventUpdate loggerEvent, Task task) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(loggerEvent.getTimestamp());
        String message = loggerEvent.getMessage();
        Optional<String> optionalEventPrefix = loggerEvent.getEventPrefix();
        if (optionalEventPrefix.isPresent()) {
            message = "[" + optionalEventPrefix.get() + "] : " + loggerEvent.getMessage();
        }
        addProcessEvent(task, offsetDateTime, loggerEvent.getLevel(), message, loggerEvent.getServiceName());
    }

    void addFileEventToTask(Task task, FileEventType fileEventType, ProcessFile processFile) {
        addFileEventToTask(task, fileEventType, processFile, FILE_EVENT_DEFAULT_LEVEL);
    }

    public void addFileEventToTask(Task task, FileEventType fileEventType, ProcessFile processFile, String logLevel) {
        final boolean isManualUpload = processFile.getFileObjectKey().contains("MANUAL_UPLOAD");
        final String message = getFileEventMessage(fileEventType, processFile.getFileType(), processFile.getFilename(), isManualUpload);
        OffsetDateTime now = OffsetDateTime.now(taskManagerConfigurationProperties.getProcessTimezone());
        addProcessEvent(task, now, logLevel, message, serviceName);
    }

    private static String getFileEventMessage(FileEventType fileEventType, String fileType, String fileName, boolean isManualUpload) {
        final String logPrefix = buildFileEventPrefix(isManualUpload);
        if (fileEventType == FileEventType.WAITING) {
            return String.format("%s new version of %s is waiting for process to end to be available : '%s'", logPrefix, fileType, fileName);
        } else if (fileEventType == FileEventType.UPDATED) {
            return String.format("%s new version of %s replaced previously available one : '%s'", logPrefix, fileType, fileName);
        } else if (fileEventType == FileEventType.AVAILABLE) {
            return String.format("%s new version of %s is available : '%s'", logPrefix, fileType, fileName);
        } else {
            return String.format("The %s : '%s' is %s", fileType, fileName, fileEventType.toString().toLowerCase());
        }
    }

    private static String buildFileEventPrefix(final boolean isManualUpload) {
        if (isManualUpload) {
            return "Manual upload of a";
        } else {
            return "A";
        }
    }

    void addProcessEvent(final Task task,
                         final OffsetDateTime timestamp,
                         final String level,
                         final String message,
                         final String serviceName) {
        processEventRepository.save(new ProcessEvent(task, timestamp, level, message, serviceName));
    }
    //////////////////////////////
    // PROCESS FILES MANAGEMENT //
    //////////////////////////////

    public Set<TaskWithStatusUpdate> addProcessFileToTasks(final ProcessFile savedProcessFile,
                                                           final FileEventType fileEventType,
                                                           final boolean isInput,
                                                           final boolean withStatusUpdate) {
        final OffsetDateTime start = savedProcessFile.getStartingAvailabilityDate();
        final OffsetDateTime end = savedProcessFile.getEndingAvailabilityDate();
        final Set<TaskWithStatusUpdate> allTasks = new HashSet<>();

        //Deal with existing tasks in database
        final Map<OffsetDateTime, TaskWithStatusUpdate> existingTasksInDatabase = taskRepository.findAllByTimestampBetween(start, end)
                .stream()
                .collect(Collectors.toMap(Task::getTimestamp, task -> new TaskWithStatusUpdate(task, false)));

        existingTasksInDatabase.values().forEach(taskWithStatusUpdate ->
                addProcessFileToExistingTasks(savedProcessFile, fileEventType, isInput, withStatusUpdate, taskWithStatusUpdate, allTasks));
        //Deal with missing tasks
        final List<Task> tasksToSave = Stream.iterate(start, time -> time.plusHours(1))
                .limit(ChronoUnit.HOURS.between(start, end))
                .filter(timestamp -> !existingTasksInDatabase.containsKey(timestamp))
                .map(Task::new)
                .toList();
        //Tasks must be saved before adding file/processEvent to it to ensure foreign key constraint is respected
        taskRepository.saveAll(tasksToSave);
        tasksToSave.forEach(newTask -> addProcessFileToNewTask(savedProcessFile, fileEventType, isInput, newTask, allTasks));
        return allTasks;
    }

    private void addProcessFileToNewTask(final ProcessFile savedProcessFile,
                                         final FileEventType fileEventType,
                                         final boolean isInput,
                                         final Task newTask,
                                         final Set<TaskWithStatusUpdate> allTasks) {
        applyProcessFileToTask(savedProcessFile, fileEventType, isInput, newTask);
        allTasks.add(new TaskWithStatusUpdate(newTask, true));
    }

    private void addProcessFileToExistingTasks(final ProcessFile savedProcessFile,
                                               final FileEventType fileEventType,
                                               final boolean isInput,
                                               final boolean withStatusUpdate,
                                               final TaskWithStatusUpdate taskWithStatusUpdate,
                                               final Set<TaskWithStatusUpdate> allTasks) {
        final Task task = taskWithStatusUpdate.getTask();
        applyProcessFileToTask(savedProcessFile, fileEventType, isInput, task);
        if (withStatusUpdate && isInput) {
            final boolean statusUpdateDueToFileArrival = checkAndUpdateTaskStatus(task, true);
            taskWithStatusUpdate.setStatusUpdated(statusUpdateDueToFileArrival);
            if (statusUpdateDueToFileArrival) {
                LOGGER.info("Update status of task with timestamp {} when processFile {} arrived to status {}",
                        task.getTimestamp(), savedProcessFile.getFilename(), task.getStatus());
            }
        }
        allTasks.add(taskWithStatusUpdate);
    }

    private void applyProcessFileToTask(final ProcessFile savedProcessFile,
                                        final FileEventType fileEventType,
                                        final boolean isInput,
                                        final Task task) {
        addFileEventToTask(task, fileEventType, savedProcessFile);
        if (isInput) {
            removeUnavailableProcessFileFromTaskRunHistory(savedProcessFile, task, fileEventType);
        }
        task.addProcessFile(savedProcessFile);
    }

    public Set<TaskWithStatusUpdate> removeProcessFileFromTasks(ProcessFile processFile) {
        return taskRepository.findAllByTimestampWithAtLeastOneProcessFileBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate())
                .parallelStream()
                .map(task -> {
                    removeUnavailableProcessFileFromTaskRunHistory(processFile, task, FileEventType.DELETED);
                    final FileRemovalStatus fileRemovalStatus = task.removeProcessFile(processFile);
                    boolean statusUpdated = false;
                    if (processFile.isInputFile()) {
                        statusUpdated = checkAndUpdateTaskStatus(task, fileRemovalStatus.fileSelectionUpdated());
                    }
                    if (task.getProcessFiles().isEmpty()) {
                        processEventRepository.deleteByTask(task);
                    } else {
                        addFileEventToTask(task, FileEventType.DELETED, processFile);
                    }
                    return new TaskWithStatusUpdate(task, statusUpdated);
                })
                .collect(Collectors.toSet());
    }

    public TaskWithStatusUpdate selectFile(final OffsetDateTime timestamp,
                                           final String filetype,
                                           final String filename) {
        Task task = taskRepository.findByTimestamp(timestamp).orElseThrow(TaskNotFoundException::new);
        if (doesStatusBlockFileSelection(task.getStatus())) {
            throw new TaskManagerException("Status of task does not allow to change selected file");
        }

        final ProcessFile processFile = task.getAvailableInputs(filetype)
                .stream()
                .filter(pf -> filename.equals(pf.getFilename()))
                .findAny()
                .orElseThrow(ProcessFileNotFoundException::new);
        task.selectProcessFile(processFile);

        String message = String.format("Manual selection of another version of %s : %s", filetype, filename);
        OffsetDateTime now = OffsetDateTime.now(taskManagerConfigurationProperties.getProcessTimezone());
        LOGGER.info("Manual selection for timestamp {} of another version of {} : {}", task.getTimestamp(), processFile.getFileType(), processFile.getFilename());
        addProcessEvent(task, now, "INFO", message, serviceName);

        boolean doesStatusNeedReset = doesStatusNeedReset(task.getStatus());
        if (doesStatusNeedReset) {
            task.setStatus(TaskStatus.READY);
        }
        return new TaskWithStatusUpdate(task, doesStatusNeedReset);
    }

    private boolean doesStatusNeedReset(final TaskStatus status) {
        return status == TaskStatus.SUCCESS || status == TaskStatus.ERROR || status == TaskStatus.INTERRUPTED;
    }

    private boolean doesStatusBlockFileSelection(final TaskStatus status) {
        return status == TaskStatus.RUNNING || status == TaskStatus.PENDING || status == TaskStatus.STOPPING;
    }

    ////////////////////////////
    // RUN HISTORY MANAGEMENT //
    ////////////////////////////

    public Task addNewRunAndSaveTask(OffsetDateTime timestamp, List<ProcessFileDto> inputFileDtos) {
        final Task task = taskRepository.findByTimestamp(timestamp).orElseThrow(TaskNotFoundException::new);
        final List<ProcessFile> inputFiles = inputFileDtos.stream()
                .filter(dto -> ProcessFileStatus.VALIDATED.equals(dto.getProcessFileStatus()))
                .map(dto -> getProcessFileFromTaskMatchingDto(task, dto))
                .toList();
        final ProcessRun processRun = new ProcessRun(inputFiles);
        task.addProcessRun(processRun);
        return taskRepository.save(task);
    }

    private static ProcessFile getProcessFileFromTaskMatchingDto(Task task, ProcessFileDto processFileDto) {
        return Stream.concat(
                        task.getAvailableInputs(processFileDto.getFileType()).stream(),
                        task.getInput(processFileDto.getFileType()).stream()
                )
                .filter(f -> f.getFilename().equals(processFileDto.getFilename()))
                .findAny()
                .orElseThrow(ProcessFileNotFoundException::new);
    }

    static void removeUnavailableProcessFileFromTaskRunHistory(ProcessFile processFile, Task task, FileEventType fileEventType) {
        if (fileEventType == FileEventType.UPDATED || fileEventType == FileEventType.DELETED) {
            task.getRunHistory().stream()
                    .filter(run -> run.getInputFiles().stream().anyMatch(runFile -> runFile.getFilename().equals(processFile.getFilename())))
                    .forEach(run -> run.removeInputFileByFilename(processFile.getFilename()));
        }
    }
}
