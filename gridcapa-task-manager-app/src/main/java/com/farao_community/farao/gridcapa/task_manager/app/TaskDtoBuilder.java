package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskDtoBuilder {

    private final TaskManagerConfigurationProperties properties;
    private final TaskRepository taskRepository;

    public TaskDtoBuilder(TaskManagerConfigurationProperties properties, TaskRepository taskRepository) {
        this.properties = properties;
        this.taskRepository = taskRepository;
    }

    public TaskDto getTaskDto(OffsetDateTime timestamp) {
        return taskRepository.findTaskByTimestampEager(timestamp)
            .map(this::createDtoFromEntity)
            .orElse(getEmptyTask(timestamp));
    }

    public TaskDto getEmptyTask(OffsetDateTime timestamp) {
        return TaskDto.emptyTask(timestamp, properties.getProcess().getInputs());
    }

    public TaskDto createDtoFromEntity(Task task) {
        return new TaskDto(
            task.getId(),
            task.getTimestamp(),
            task.getStatus(),
            properties.getProcess().getInputs().stream()
                .map(input -> task.getProcessFile(input)
                    .map(this::createDtofromEntity)
                    .orElseGet(() -> ProcessFileDto.emptyProcessFile(input)))
                .collect(Collectors.toList()),
            task.getProcessEvents().stream().map(ProcessEvent::createDtoFromEntity).collect(Collectors.toList()));
    }

    public ProcessFileDto createDtofromEntity(ProcessFile processFile) {
        return new ProcessFileDto(
            processFile.getFileType(),
            ProcessFileStatus.VALIDATED,
            processFile.getFilename(),
            processFile.getLastModificationDate(),
            processFile.getFileUrl());
    }
}
