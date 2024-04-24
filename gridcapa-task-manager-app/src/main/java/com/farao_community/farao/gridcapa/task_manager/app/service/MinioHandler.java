/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.ProcessFileRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.TaskWithStatusUpdate;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.FileEventType;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFileMinio;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import io.minio.messages.Event;
import io.minio.messages.NotificationRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties.TASK_MANAGER_LOCK;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class MinioHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinioHandler.class);
    public static final String FILE_GROUP_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_GROUP_METADATA_KEY;
    public static final String FILE_TARGET_PROCESS_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TARGET_PROCESS_METADATA_KEY;
    public static final String FILE_TYPE_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TYPE_METADATA_KEY;
    public static final String FILE_VALIDITY_INTERVAL_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_VALIDITY_INTERVAL_METADATA_KEY;
    private static final String FILE_EVENT_DEFAULT_LEVEL = "INFO";
    public static final String PROCESS_FILE_REMOVED_MESSAGE = "process file {} was removed from waiting list";

    private final ProcessFileRepository processFileRepository;
    private final Logger businessLogger;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final TaskUpdateNotifier taskUpdateNotifier;
    private final List<ProcessFileMinio> waitingFilesList = new ArrayList<>();
    @Value("${spring.application.name}")
    private String serviceName;

    public MinioHandler(ProcessFileRepository processFileRepository, Logger businessLogger, TaskManagerConfigurationProperties taskManagerConfigurationProperties, TaskRepository taskRepository, TaskUpdateNotifier taskUpdateNotifier) {
        this.processFileRepository = processFileRepository;
        this.businessLogger = businessLogger;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskRepository = taskRepository;
        this.taskUpdateNotifier = taskUpdateNotifier;
    }

    @Bean
    @Transactional
    public Consumer<Flux<NotificationRecords>> consumeMinioEvent() {
        return f -> f.subscribe(nr -> {
            try {
                handleMinioEvent(nr);
            } catch (Exception e) {
                LOGGER.error("Unable to handle MinIO event properly", e);
            }
        });
    }

    @Transactional
    public void handleMinioEvent(NotificationRecords notificationRecords) {
        notificationRecords.events().forEach(event -> {
            LOGGER.debug("s3 event received");
            switch (event.eventType()) {
                case OBJECT_CREATED_ANY:
                case OBJECT_CREATED_PUT:
                case OBJECT_CREATED_POST:
                case OBJECT_CREATED_COPY:
                case OBJECT_CREATED_COMPLETE_MULTIPART_UPLOAD:
                    updateTasks(event);
                    break;
                case OBJECT_REMOVED_ANY:
                case OBJECT_REMOVED_DELETE:
                case OBJECT_REMOVED_DELETED_MARKER_CREATED:
                    removeProcessFile(event);
                    break;
                default:
                    LOGGER.info("S3 event type {} not handled by task manager", event.eventType());
                    break;
            }
        });
    }

    @Transactional
    public void updateTasks(Event event) {
        synchronized (TASK_MANAGER_LOCK) {
            if (!event.userMetadata().isEmpty() && taskManagerConfigurationProperties.getProcess().getTag().equals(event.userMetadata().get(FILE_TARGET_PROCESS_METADATA_KEY))) {
                ProcessFileMinio processFileMinio = buildProcessFileMinioFromEvent(event);
                if (processFileMinio != null) {
                    boolean isInput = MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(event.userMetadata().get(FILE_GROUP_METADATA_KEY));
                    ProcessFile processFile = processFileMinio.getProcessFile();
                    if (!isInput) {
                        Set<TaskWithStatusUpdate> taskWithStatusUpdates = addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), false, false);
                        saveAndNotifyTasks(taskWithStatusUpdates);
                        LOGGER.info("Process file {} has been added properly", processFile.getFilename());
                    } else {
                        // If the file coming is an input while one of the concerned timestamp is running, the file put in a waiting list until the process ends
                        Set<Task> tasksForProcessFile = taskRepository.findAllByTimestampBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate());
                        if (isAnyTaskRunningOrPending(tasksForProcessFile)) {
                            addWaitingFileAndNotifyTasks(processFileMinio, tasksForProcessFile);
                        } else {
                            Set<TaskWithStatusUpdate> taskWithStatusUpdates = addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), true, true);
                            saveAndNotifyTasks(taskWithStatusUpdates);
                            LOGGER.info("Process file {} has been added properly", processFile.getFilename());
                        }
                    }
                } else {
                    String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
                    LOGGER.warn("Minio object {} has not been added ", objectKey);
                }
            }
        }
    }

    boolean isAnyTaskRunningOrPending(Set<Task> tasks) {
        return tasks.stream().anyMatch(task -> task.getStatus().equals(TaskStatus.RUNNING) || task.getStatus().equals(TaskStatus.PENDING));
    }

    private ProcessFileMinio buildProcessFileMinioFromEvent(Event event) {
        String validityInterval = event.userMetadata().get(FILE_VALIDITY_INTERVAL_METADATA_KEY);
        String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
        if (validityInterval != null && !validityInterval.isEmpty()) {
            String fileGroup = event.userMetadata().get(FILE_GROUP_METADATA_KEY);
            String fileType = event.userMetadata().get(FILE_TYPE_METADATA_KEY);
            LOGGER.info("Adding MinIO object {}", objectKey);
            String[] interval = validityInterval.split("/");
            return getProcessFileMinio(
                    OffsetDateTime.parse(interval[0]),
                    OffsetDateTime.parse(interval[1]),
                    objectKey,
                    fileType,
                    fileGroup);
        } else {
            return null;
        }
    }

    private ProcessFileMinio getProcessFileMinio(OffsetDateTime startTime, OffsetDateTime endTime, String objectKey, String fileType, String fileGroup) {
        /*
        This implies that only one file per type and group can exist. If another one is imported it would just
        replace the previous one.
        */
        Optional<ProcessFile> optProcessFile = processFileRepository.findByStartingAvailabilityDateAndFileTypeAndGroup(startTime, fileType, fileGroup);
        final boolean isManualUpload = objectKey.contains("MANUAL_UPLOAD");
        final String logPrefix = buildBusinessLogPrefix(isManualUpload);
        if (optProcessFile.isPresent()) {
            LOGGER.info("File {} available at {} is already referenced in the database. Updating process file data.", fileType, startTime);
            ProcessFile processFile = optProcessFile.get();
            processFile.setFileObjectKey(objectKey);
            processFile.setLastModificationDate(getProcessNow());
            businessLogger.info("{} new version of {} replaced previously available one : {}.", logPrefix, fileType, processFile.getFilename());
            return new ProcessFileMinio(processFile, FileEventType.UPDATED);
        } else {
            LOGGER.info("Creating a new file {} available at {}.", fileType, startTime);
            ProcessFile processFile = new ProcessFile(objectKey, fileGroup, fileType, startTime, endTime, getProcessNow());
            businessLogger.info("{} new version of {} is available : {}", logPrefix, fileType, processFile.getFilename());
            return new ProcessFileMinio(processFile, FileEventType.AVAILABLE);
        }
    }

    private static String buildBusinessLogPrefix(final boolean isManualUpload) {
        if (isManualUpload) {
            return "Manual upload of a";
        } else {
            return "A";
        }
    }

    private void addWaitingFileAndNotifyTasks(ProcessFileMinio processFileMinio, Set<Task> tasks) {
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.PENDING) {
                removeWaitingFileWithSameTypeAndValidity(processFileMinio);
                waitingFilesList.add(processFileMinio);
                LOGGER.info("process file {} is added to waiting files list", processFileMinio.getProcessFile().getFilename());
                addFileEventToTask(task, FileEventType.WAITING, processFileMinio.getProcessFile(), "WARN");
                saveAndNotifyTasks(Collections.singleton(new TaskWithStatusUpdate(task, false))); //No need to update status when the file is waiting
            }
        }
    }

    private void removeWaitingFileWithSameTypeAndValidity(ProcessFileMinio newProcessFileMinio) {
        Set<ProcessFileMinio> processFileMiniosWaiting = waitingFilesList.stream().filter(processFileMinio -> processFileMinio.hasSameTypeAndValidity(newProcessFileMinio)).collect(Collectors.toSet());
        for (ProcessFileMinio processFileMinio : processFileMiniosWaiting) {
            waitingFilesList.remove(processFileMinio);
            LOGGER.info(PROCESS_FILE_REMOVED_MESSAGE, processFileMinio.getProcessFile().getFilename());
        }
    }

    private OffsetDateTime getProcessNow() {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
        return OffsetDateTime.now(ZoneId.of(processProperties.getTimezone()));
    }

    private Set<TaskWithStatusUpdate> addProcessFileToTasks(ProcessFile processFile, FileEventType fileEventType, boolean isInput, boolean withStatusUpdate) {
        final ProcessFile savedProcessFile = processFileRepository.save(processFile);
        Set<TaskWithStatusUpdate> taskWithStatusUpdateSet = Stream.iterate(savedProcessFile.getStartingAvailabilityDate(), time -> time.plusHours(1))
                .limit(ChronoUnit.HOURS.between(savedProcessFile.getStartingAvailabilityDate(), savedProcessFile.getEndingAvailabilityDate()))
                .parallel()
                .map(this::getTaskWithStatusUpdate) //by default statusUpdated false except if task is created
                .collect(Collectors.toSet());

        for (TaskWithStatusUpdate taskWithStatusUpdate : taskWithStatusUpdateSet) {
            Task task = taskWithStatusUpdate.getTask();
            addFileEventToTask(task, fileEventType, savedProcessFile);
            task.addProcessFile(savedProcessFile);
            if (withStatusUpdate && !taskWithStatusUpdate.isStatusUpdated() && isInput) {
                //if taskWithStatusUpdate is already false and the process file of type input, we need to check if the status should be updated
                boolean statusUpdateDueToFileArrival = checkAndUpdateTaskStatus(task, true);
                taskWithStatusUpdate.setStatusUpdated(statusUpdateDueToFileArrival);
                LOGGER.info("Update task status when processFile {} arrived to status {}", savedProcessFile.getFilename(), task.getStatus());
            }
        }
        return taskWithStatusUpdateSet;
    }

    private TaskWithStatusUpdate getTaskWithStatusUpdate(OffsetDateTime timestamp) {
        return taskRepository.findByTimestamp(timestamp)
                .map(task -> new TaskWithStatusUpdate(task, false))
                .orElseGet(() -> new TaskWithStatusUpdate(taskRepository.save(new Task(timestamp)), true));
    }

    private void addFileEventToTask(Task task, FileEventType fileEventType, ProcessFile processFile) {
        addFileEventToTask(task, fileEventType, processFile, FILE_EVENT_DEFAULT_LEVEL);
    }

    private void addFileEventToTask(Task task, FileEventType fileEventType, ProcessFile processFile, String logLevel) {
        String message = getFileEventMessage(fileEventType, processFile.getFileType(), processFile.getFilename());
        task.addProcessEvent(getProcessNow(), logLevel, message, serviceName);
    }

    private String getFileEventMessage(FileEventType fileEventType, String fileType, String fileName) {
        if (fileEventType.equals(FileEventType.WAITING)) {
            return String.format("A new version of %s is waiting for process to end to be available : '%s'", fileType, fileName);
        } else if (fileEventType.equals(FileEventType.UPDATED)) {
            return String.format("A new version of %s is available : '%s'", fileType, fileName);
        } else {
            return String.format("The %s : '%s' is %s", fileType, fileName, fileEventType.toString().toLowerCase());
        }
    }

    public void emptyWaitingList(OffsetDateTime timestamp) {
        LOGGER.info("Handle Emptying of waiting list..");
        LOGGER.info("Waiting list contains {} files", waitingFilesList.size());
        List<ProcessFileMinio> waitingProcessFilesToAdd = getWaitingProcessFilesForTimestamp(timestamp);
        int processFilesSize = waitingProcessFilesToAdd.size();

        if (processFilesSize >= 1) {
            // each process file is added to the task, but the status is updated only for the last file waiting (withStatusUpdate = true parameter)
            for (int i = 0; i < processFilesSize - 1; i++) {
                ProcessFileMinio processFileMinio = waitingProcessFilesToAdd.get(i);
                ProcessFile processFile = processFileMinio.getProcessFile();
                Set<TaskWithStatusUpdate> tasksWithStatusUpdate = addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), true, false);
                saveAndNotifyTasks(tasksWithStatusUpdate);
                waitingFilesList.remove(processFileMinio);
                LOGGER.info(PROCESS_FILE_REMOVED_MESSAGE, processFile.getFilename());
            }
            ProcessFileMinio lastProcessFileMinio = waitingProcessFilesToAdd.get(processFilesSize - 1);
            Set<TaskWithStatusUpdate> tasksWithStatusUpdate = addProcessFileToTasks(lastProcessFileMinio.getProcessFile(), lastProcessFileMinio.getFileEventType(), true, true);
            saveAndNotifyTasks(tasksWithStatusUpdate);
            waitingFilesList.remove(lastProcessFileMinio);
            LOGGER.info(PROCESS_FILE_REMOVED_MESSAGE, lastProcessFileMinio.getProcessFile().getFilename());
        }
    }

    List<ProcessFileMinio> getWaitingProcessFilesForTimestamp(OffsetDateTime timestamp) {
        List<ProcessFileMinio> processFilesWithFinishedTasks = new ArrayList<>();
        for (ProcessFileMinio processFileMinio : waitingFilesList) {
            ProcessFile processFile = processFileMinio.getProcessFile();
            if (isFileValidForTimestamp(timestamp, processFile)) {
                Set<Task> taskForProcessFile = taskRepository.findAllByTimestampBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate());
                if (!isAnyTaskRunningOrPending(taskForProcessFile)) {
                    processFilesWithFinishedTasks.add(processFileMinio);
                    LOGGER.info("process file to add {} for timestamp {}", processFileMinio.getProcessFile().getFilename(), timestamp);
                }
            }
        }
        return processFilesWithFinishedTasks;
    }

    private boolean isFileValidForTimestamp(OffsetDateTime timestamp, ProcessFile processFile) {
        OffsetDateTime startingAvailabilityDate = processFile.getStartingAvailabilityDate();
        OffsetDateTime endingAvailabilityDate = processFile.getEndingAvailabilityDate();
        return (timestamp.equals(startingAvailabilityDate) || timestamp.isAfter(startingAvailabilityDate)) && timestamp.isBefore(endingAvailabilityDate);
    }

    /**
     * We compare the size of inputs list from process files of the task and the size of inputs from configuration.
     * If its equal task is ready otherwise it is created. When it is null it is not created.
     * This works because we consider there are only one file type per inputs. We call this method at adding and
     * deletion to check if the status has changed.
     *
     * @param task:                      Task on which to evaluate the status.
     * @param inputFileSelectionChanged: boolean indicating whether an input file has been changed
     */
    private boolean checkAndUpdateTaskStatus(Task task, boolean inputFileSelectionChanged) {
        TaskStatus initialTaskStatus = task.getStatus();
        List<String> inputFileTypes = task.getProcessFiles().stream()
                .filter(processFile -> MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(processFile.getFileGroup()))
                .map(ProcessFile::getFileType).toList();
        if (inputFileTypes.isEmpty()) {
            task.setStatus(TaskStatus.NOT_CREATED);
        } else if (inputFileSelectionChanged && new HashSet<>(inputFileTypes).containsAll(taskManagerConfigurationProperties.getProcess().getInputs())) {
            task.setStatus(TaskStatus.READY);
        } else if (inputFileSelectionChanged) {
            task.setStatus(TaskStatus.CREATED);
        }
        return initialTaskStatus != task.getStatus();
    }

    private void saveAndNotifyTasks(Set<TaskWithStatusUpdate> taskWithStatusUpdateSet) {
        LOGGER.debug("Saving related tasks in DB");
        taskRepository.saveAllAndFlush(taskWithStatusUpdateSet.stream().map(TaskWithStatusUpdate::getTask).collect(Collectors.toList()));
        LOGGER.debug("Notifying on web-sockets");
        taskUpdateNotifier.notify(taskWithStatusUpdateSet);
    }

    @Transactional
    public void removeProcessFile(Event event) {
        synchronized (TASK_MANAGER_LOCK) {
            String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
            LOGGER.info("Removing MinIO object {}", objectKey);
            Optional<ProcessFile> optionalProcessFile = processFileRepository.findByFileObjectKey(objectKey);
            if (optionalProcessFile.isPresent()) {
                ProcessFile processFile = optionalProcessFile.get();
                LOGGER.debug("Finding tasks related to {}", processFile.getFilename());
                saveAndNotifyTasks(removeProcessFileFromTasks(processFile));
                processFileRepository.delete(processFile);
                LOGGER.info("Process file {} has been removed properly", processFile.getFilename());
            } else {
                LOGGER.info("File not referenced in the database. Nothing to do.");
            }
        }
    }

    private Set<TaskWithStatusUpdate> removeProcessFileFromTasks(ProcessFile processFile) {
        return taskRepository.findAllByTimestampBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate())
                .parallelStream()
                .map(task -> {
                    final Task.FileRemovalStatus fileRemovalStatus = task.removeProcessFile(processFile);
                    boolean statusUpdated = false;
                    if (MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(processFile.getFileGroup())) {
                        statusUpdated = checkAndUpdateTaskStatus(task, fileRemovalStatus.newSelectedFile());
                    }
                    if (task.getProcessFiles().isEmpty()) {
                        task.getProcessEvents().clear();
                    } else {
                        addFileEventToTask(task, FileEventType.DELETED, processFile);
                    }
                    return new TaskWithStatusUpdate(task, statusUpdated);
                })
                .collect(Collectors.toSet());
    }

}
