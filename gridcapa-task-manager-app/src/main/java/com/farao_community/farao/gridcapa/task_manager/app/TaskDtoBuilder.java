package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.TaskManagerConfigurationProperties;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskDtoBuilder {

    @Autowired
    private TaskManagerConfigurationProperties properties;

    @Autowired
    private TaskRepository taskRepository;

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
                    .map(ProcessFile::createDtofromEntity)
                    .orElseGet(() -> ProcessFileDto.emptyProcessFile(input)))
                .collect(Collectors.toList()),
            task.getProcessEvents().stream().map(ProcessEvent::createDtoFromEntity).collect(Collectors.toList()));
    }
}
