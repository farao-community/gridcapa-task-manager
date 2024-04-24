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
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class FileSelectorService {

    private final TaskRepository taskRepository;
    private final Logger businessLogger;
    private final TaskUpdateNotifier taskUpdateNotifier;

    public FileSelectorService(final TaskRepository taskRepository,
                               final Logger businessLogger,
                               final TaskUpdateNotifier taskUpdateNotifier) {
        this.taskRepository = taskRepository;
        this.businessLogger = businessLogger;
        this.taskUpdateNotifier = taskUpdateNotifier;
    }

    public void selectFile(final OffsetDateTime timestamp,
                           final String filetype,
                           final String filename) {
        synchronized (TaskManagerConfigurationProperties.TASK_MANAGER_LOCK) {
            final Task task = taskRepository.findByTimestamp(timestamp).orElseThrow(TaskNotFoundException::new);
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
            businessLogger.info("Manual selection of another version of {} : {}", filetype, filename);
            boolean doesStatusNeedReset = doesStatusNeedReset(task.getStatus());
            if (doesStatusNeedReset) {
                task.setStatus(TaskStatus.READY);
            }
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
