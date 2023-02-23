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
    private static final Object LOCK = new Object();
    public static final String FILE_GROUP_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_GROUP_METADATA_KEY;
    public static final String FILE_TARGET_PROCESS_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TARGET_PROCESS_METADATA_KEY;
    public static final String FILE_TYPE_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_TYPE_METADATA_KEY;
    public static final String FILE_VALIDITY_INTERVAL_METADATA_KEY = MinioAdapterConstants.DEFAULT_GRIDCAPA_FILE_VALIDITY_INTERVAL_METADATA_KEY;
    private static final String FILE_EVENT_DEFAULT_LEVEL = "INFO";

    private final ProcessFileRepository processFileRepository;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskRepository taskRepository;
    private final TaskUpdateNotifier taskUpdateNotifier;
    private final List<ProcessFileMinio> listWaitingFile = new ArrayList<>();

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
        synchronized (LOCK) {
            TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
            if (!event.userMetadata().isEmpty() && processProperties.getTag().equals(event.userMetadata().get(FILE_TARGET_PROCESS_METADATA_KEY))) {
                ProcessFileMinio processFileMinio = buildProcessFileMinioFromEvent(event);
                if (processFileMinio != null) {
                    boolean isInput = MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(event.userMetadata().get(FILE_GROUP_METADATA_KEY));
                    // If the file coming is an input and one of the concerned timestamp is running, the file is not saved
                    if (!isInput) {
                        saveProcessFile(processFileMinio, false);
                    } else {
                        if (!isRunning(processFileMinio)) {
                            saveProcessFile(processFileMinio, true);
                        }
                    }
                } else {
                    String objectKey = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
                    LOGGER.warn("Minio object {} has not been added ", objectKey);
                }
            }
        }
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

    private void saveProcessFile(ProcessFileMinio processFileMinio, boolean isInput) {
        final ProcessFile savedProcessFile = processFileRepository.save(processFileMinio.getProcessFile());
        Set<TaskWithStatusUpdate> taskWithStatusUpdates = addProcessFileToTasks(savedProcessFile, processFileMinio.getFileEventType(), isInput);
        saveAndNotifyTasks(taskWithStatusUpdates);
        LOGGER.info("Process file {} has been added properly", processFileMinio.getProcessFile().getFilename());
    }

    private boolean isRunning(ProcessFileMinio processFileMinio) {
        ProcessFile processFile = processFileMinio.getProcessFile();

        List<OffsetDateTime> listTimestamps = Stream.iterate(processFile.getStartingAvailabilityDate(), time -> time.plusHours(1))
                .limit(ChronoUnit.HOURS.between(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate())).collect(Collectors.toList());

        List<TaskWithStatusUpdate> listTaskWithStatusUpdate = findAllTaskByTimestamp(listTimestamps);

        for (TaskWithStatusUpdate taskWithStatusUpdate : listTaskWithStatusUpdate) {
            Task task = taskWithStatusUpdate.getTask();
            if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.PENDING) {
                addFileEventToTask(task, FileEventType.WAITING, processFileMinio.getProcessFile());
                saveAndNotifyTasks(Collections.singleton(new TaskWithStatusUpdate(task, true)));
                listWaitingFile.add(processFileMinio);
                return true;
            }
        }

        return false;
    }

    private OffsetDateTime getProcessNow() {
        TaskManagerConfigurationProperties.ProcessProperties processProperties = taskManagerConfigurationProperties.getProcess();
        return OffsetDateTime.now(ZoneId.of(processProperties.getTimezone()));
    }

    private Set<TaskWithStatusUpdate> addProcessFileToTasks(ProcessFile processFile, FileEventType fileEventType, boolean isInput) {
        Set<TaskWithStatusUpdate> setTaskWithStatusUpdate = new HashSet<>();

        List<OffsetDateTime> listTimestamps = Stream.iterate(processFile.getStartingAvailabilityDate(), time -> time.plusHours(1))
                .limit(ChronoUnit.HOURS.between(processFile.getStartingAvailabilityDate(), processFile.getEndingAvailabilityDate())).collect(Collectors.toList());

        List<TaskWithStatusUpdate> listTaskWithStatusUpdate = findAllTaskByTimestamp(listTimestamps);

        for (TaskWithStatusUpdate taskWithStatusUpdate : listTaskWithStatusUpdate) {
            Task task = taskWithStatusUpdate.getTask();
            addFileEventToTask(task, fileEventType, processFile);
            task.addProcessFile(processFile);
            // If the task is created the status will be set as updated whatever the check gives as output
            // and in case the task was not created here, it will depend on the output of the check.
            boolean statusUpdateDueToFileArrival = false;
            if (isInput) {
                statusUpdateDueToFileArrival = checkAndUpdateTaskStatus(task);
            }
            taskWithStatusUpdate.setStatusUpdated(statusUpdateDueToFileArrival || taskWithStatusUpdate.isStatusUpdated());
            setTaskWithStatusUpdate.add(taskWithStatusUpdate);
        }

        return setTaskWithStatusUpdate;
    }

    private List<TaskWithStatusUpdate> findAllTaskByTimestamp(List<OffsetDateTime> listTimestamps) {
        return listTimestamps.stream().map(ts -> taskRepository.findByTimestamp(ts)
                        .map(task -> new TaskWithStatusUpdate(task, false))
                        .orElseGet(() -> new TaskWithStatusUpdate(taskRepository.save(new Task(ts)), true)))
                .collect(Collectors.toList());
    }

    private void addFileEventToTask(Task task, FileEventType fileEventType, ProcessFile processFile) {
        String message = getFileEventMessage(fileEventType, processFile.getFileType(), processFile.getFilename());
        task.addProcessEvent(getProcessNow(), FILE_EVENT_DEFAULT_LEVEL, message);
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

    public void emptyWaitingList() {
        for (ProcessFileMinio processFileMinio : listWaitingFile) {
            saveProcessFile(processFileMinio, true);
        }
        listWaitingFile.clear();
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
        LOGGER.debug("Saving related tasks");
        taskRepository.saveAll(taskWithStatusUpdateSet.stream().map(TaskWithStatusUpdate::getTask).collect(Collectors.toSet()));
        LOGGER.debug("Notifying on web-sockets");
        taskUpdateNotifier.notify(taskWithStatusUpdateSet);
    }

    public void removeProcessFile(Event event) {
        synchronized (LOCK) {
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
}
