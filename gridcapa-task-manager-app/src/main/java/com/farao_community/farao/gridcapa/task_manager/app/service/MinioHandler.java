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
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
@Service
public class MinioHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinioHandler.class);
    public static final Object LOCK_MINIO_AND_STATUS_HANDLER = new Object();
    public static final String FILE_GROUP_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_GROUP_METADATA_KEY;
    public static final String FILE_TARGET_PROCESS_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TARGET_PROCESS_METADATA_KEY;
    public static final String FILE_TYPE_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TYPE_METADATA_KEY;
    public static final String FILE_VALIDITY_INTERVAL_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_VALIDITY_INTERVAL_METADATA_KEY;
    private static final String FILE_EVENT_DEFAULT_LEVEL = "INFO";

    private final ProcessFileRepository processFileRepository;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final TaskUpdateNotifier taskUpdateNotifier;
    private HashMap<ProcessFileMinio, List<OffsetDateTime>> mapWaitingFilesNew = new HashMap<>(); //todo it's better to use linkedMap , check if we need the list of offset date time because already having interval

    public MinioHandler(ProcessFileRepository processFileRepository, TaskManagerConfigurationProperties taskManagerConfigurationProperties, TaskRepository taskRepository, TaskUpdateNotifier taskUpdateNotifier) {
        this.processFileRepository = processFileRepository;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskRepository = taskRepository;
        this.taskUpdateNotifier = taskUpdateNotifier;
    }

    @Bean
    public Consumer<Flux<NotificationRecords>> consumeMinioEvent() {
        return f -> f.subscribe(nr -> {
            try {
                handleMinioEvent(nr);
            } catch (Exception e) {
                LOGGER.error("Unable to handle MinIO event properly", e);
            }
        });
    }

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

    public void updateTasks(Event event) {
        synchronized (LOCK_MINIO_AND_STATUS_HANDLER) {
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
                        if (isTasksRunningOrPending(tasksForProcessFile)) {
                            addWaintingFileAndNotifyTasks(processFileMinio, tasksForProcessFile);
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

    boolean isTasksRunningOrPending(Set<Task> tasks) {
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
        if (optProcessFile.isPresent()) {
            LOGGER.info("File {} available at {} is already referenced in the database. Updating process file data.", fileType, startTime);
            ProcessFile processFile = optProcessFile.get();
            processFile.setFileObjectKey(objectKey);
            processFile.setLastModificationDate(getProcessNow());
            return new ProcessFileMinio(processFile, FileEventType.UPDATED);
        } else {
            LOGGER.info("Creating a new file {} available at {}.", fileType, startTime);
            ProcessFile processFile = new ProcessFile(objectKey, fileGroup, fileType, startTime, endTime, getProcessNow());
            return new ProcessFileMinio(processFile, FileEventType.AVAILABLE);
        }
    }

    private void addWaintingFileAndNotifyTasks(ProcessFileMinio processFileMinio, Set<Task> tasks) {
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.PENDING) {
                removeWaitingFileWithSameTypeAndValidity(processFileMinio);
                LOGGER.info("process file " + processFileMinio.getProcessFile().getFilename() + " is added to waiting map");
                mapWaitingFilesNew.put(processFileMinio, tasks.stream().map(Task::getTimestamp).collect(Collectors.toList()));
                addFileEventToTask(task, FileEventType.WAITING, processFileMinio.getProcessFile(), "WARN");
                saveAndNotifyTasks(Collections.singleton(new TaskWithStatusUpdate(task, false))); //No need to update status when the file is waiting
            }
        }
    }

    private void removeWaitingFileWithSameTypeAndValidity(ProcessFileMinio newProcessFileMinio) {
        Set<ProcessFileMinio> processFileMiniosWaiting = mapWaitingFilesNew.keySet().stream().filter(processFileMinio -> processFileMinio.hasSameTypeAndValidity(newProcessFileMinio)).collect(Collectors.toSet());
        for (ProcessFileMinio processFileMinio : processFileMiniosWaiting) {
            mapWaitingFilesNew.remove(processFileMinio);
            LOGGER.info("processFile {} was removed from waiting list", processFileMinio.getProcessFile().getFilename());
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
                boolean statusUpdateDueToFileArrival = checkAndUpdateTaskStatus(task);
                taskWithStatusUpdate.setStatusUpdated(statusUpdateDueToFileArrival);
                LOGGER.info("Update task status when processFile " + savedProcessFile.getFilename() + " arrived to status  " + task.getStatus());
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
        task.addProcessEvent(getProcessNow(), logLevel, message);
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
        LOGGER.info("Empty waiting list..");
        List<ProcessFileMinio> waitingProcessFilesToAdd = getWaitingProcessFilesForTimestamp(timestamp);
        int processFilesSize = waitingProcessFilesToAdd.size();

        if (processFilesSize >= 1) {
            for (int i = 0; i < processFilesSize - 1; i++) {
                ProcessFileMinio processFileMinio = waitingProcessFilesToAdd.get(i);
                ProcessFile processFile = processFileMinio.getProcessFile();
                // each process file is added to the task, but the status is updated only for the last file waiting
                Set<TaskWithStatusUpdate> tasksWithStatusUpdate = addProcessFileToTasks(processFile, processFileMinio.getFileEventType(), true, false);
                saveAndNotifyTasks(tasksWithStatusUpdate);
                mapWaitingFilesNew.remove(processFileMinio);
                LOGGER.info("processFile {} was removed from waiting list", processFile.getFilename());
            }
            ProcessFileMinio lastProcessFileMinio = waitingProcessFilesToAdd.get(processFilesSize - 1);
            Set<TaskWithStatusUpdate> tasksWithStatusUpdate = addProcessFileToTasks(lastProcessFileMinio.getProcessFile(), lastProcessFileMinio.getFileEventType(), true, true);
            saveAndNotifyTasks(tasksWithStatusUpdate);
            mapWaitingFilesNew.remove(lastProcessFileMinio);
            LOGGER.info("processFile {} was removed from waiting list", lastProcessFileMinio.getProcessFile().getFilename());
        }
    }

    List<ProcessFileMinio> getWaitingProcessFilesForTimestamp(OffsetDateTime timestamp) { //todo refactor with processfile validity interval
        List<ProcessFileMinio> processFilesWithFinishedTasks = new ArrayList<>();
        for (Map.Entry<ProcessFileMinio, List<OffsetDateTime>> entry : mapWaitingFilesNew.entrySet()) {
            List<OffsetDateTime> listTimestamps = entry.getValue();
            ProcessFile processFile = entry.getKey().getProcessFile();
            if (listTimestamps.contains(timestamp)) {
                Set<Task> taskForProcessFile = taskRepository.findAllByTimestampBetween(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate());
                if (!isTasksRunningOrPending(taskForProcessFile)) {
                    processFilesWithFinishedTasks.add(entry.getKey());
                }
            }
        }
        return processFilesWithFinishedTasks;
    }

    /**
     * We compare the size of inputs list from process files of the task and the size of inputs from configuration.
     * If its equal task is ready otherwise it is created. When it is null it is not created.
     * This works because we consider there are only one file type per inputs. We call this method at adding and
     * deletion to check if the status has changed.
     *
     * @param task: Task on which to evaluate the status.
     */
    private boolean checkAndUpdateTaskStatus(Task task) {
        TaskStatus initialTaskStatus = task.getStatus();
        List<String> availableInputFileTypes = task.getProcessFiles().stream()
                .filter(processFile -> MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(processFile.getFileGroup()))
                .map(ProcessFile::getFileType).collect(Collectors.toList());
        if (availableInputFileTypes.isEmpty()) {
            task.setStatus(TaskStatus.NOT_CREATED);
        } else if (new HashSet<>(availableInputFileTypes).containsAll(taskManagerConfigurationProperties.getProcess().getInputs())) {
            task.setStatus(TaskStatus.READY);
        } else {
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

    public void removeProcessFile(Event event) {
        synchronized (LOCK_MINIO_AND_STATUS_HANDLER) {
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
                    task.removeProcessFile(processFile);
                    boolean statusUpdated = false;
                    if (MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(processFile.getFileGroup())) {
                        statusUpdated = checkAndUpdateTaskStatus(task);
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

    void setMapWaitingFilesNew(Map<ProcessFileMinio, List<OffsetDateTime>> mapWaitingFilesNew) {
        this.mapWaitingFilesNew = (HashMap<ProcessFileMinio, List<OffsetDateTime>>) mapWaitingFilesNew;
    }
}
