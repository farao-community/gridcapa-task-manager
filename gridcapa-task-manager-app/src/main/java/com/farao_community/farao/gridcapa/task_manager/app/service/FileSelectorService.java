package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class FileSelectorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSelectorService.class);

    private final TaskRepository taskRepository;
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;
    private final TaskUpdateNotifier taskUpdateNotifier;

    @Value("${spring.application.name}")
    private String serviceName;

    public FileSelectorService(TaskRepository taskRepository,
                               TaskManagerConfigurationProperties taskManagerConfigurationProperties,
                               TaskUpdateNotifier taskUpdateNotifier) {
        this.taskRepository = taskRepository;
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
        this.taskUpdateNotifier = taskUpdateNotifier;
    }

    public void selectFile(final OffsetDateTime timestamp,
                           final String filetype,
                           final String filename) {
        LOGGER.info("Selecting file '{}' for input type {} in task {}", filename, filetype, timestamp);
        synchronized (TaskManagerConfigurationProperties.TASK_MANAGER_LOCK) {
            Task task = taskRepository.findByTimestamp(timestamp).orElseThrow(TaskNotFoundException::new);
            // todo comportement à confirmer
            if (doesStatusBlockFileSelection(task.getStatus())) {
                throw new TaskManagerException("Status of task does not allow to change selected file");
            }
            //
            final ProcessFile processFile = task.getAvailableInputs(filetype)
                    .stream()
                    .filter(pf -> filename.equals(pf.getFilename()))
                    .findAny()
                    .orElseThrow(ProcessFileNotFoundException::new);
            task.selectProcessFile(processFile);

            String message = String.format("Manual selection of another version of %s : %s", filetype, filename);
            OffsetDateTime now = OffsetDateTime.now(taskManagerConfigurationProperties.getProcessTimezone());
            task.addProcessEvent(now, "INFO", message, serviceName);

            boolean doesStatusNeedReset = doesStatusNeedReset(task.getStatus());
            if (doesStatusNeedReset) {
                task.setStatus(TaskStatus.READY);
            }
            task = taskRepository.save(task);
            taskUpdateNotifier.notify(task, doesStatusNeedReset, false);
        }
    }

    private boolean doesStatusNeedReset(final TaskStatus status) {
        return TaskStatus.SUCCESS == status || TaskStatus.ERROR == status || TaskStatus.INTERRUPTED == status;
    }

    private boolean doesStatusBlockFileSelection(final TaskStatus status) {
        return TaskStatus.RUNNING == status || TaskStatus.PENDING == status || TaskStatus.STOPPING == status;
    }
}
