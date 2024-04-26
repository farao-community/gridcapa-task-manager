package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.TaskUpdateNotifier;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.util.Optional;

@SpringBootTest
class FileSelectorServiceTest {
    @MockBean
    private TaskRepository taskRepository;
    @MockBean
    private TaskUpdateNotifier taskUpdateNotifier;
    @Autowired
    private FileSelectorService fileSelectorService;

    @Test
    void selectFileWithNoTaskFound() {
        OffsetDateTime now = OffsetDateTime.now();
        Mockito.when(taskRepository.findByTimestamp(now)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> fileSelectorService.selectFile(now, null, null))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void selectFileWithBlockingTaskStatus() {
        OffsetDateTime now = OffsetDateTime.now();
        Task task = new Task(now);
        Mockito.when(taskRepository.findByTimestamp(now)).thenReturn(Optional.of(task));

        task.setStatus(TaskStatus.PENDING);
        Assertions.assertThatThrownBy(() -> fileSelectorService.selectFile(now, null, null))
                .isInstanceOf(TaskManagerException.class);

        task.setStatus(TaskStatus.RUNNING);
        Assertions.assertThatThrownBy(() -> fileSelectorService.selectFile(now, null, null))
                .isInstanceOf(TaskManagerException.class);

        task.setStatus(TaskStatus.STOPPING);
        Assertions.assertThatThrownBy(() -> fileSelectorService.selectFile(now, null, null))
                .isInstanceOf(TaskManagerException.class);
    }

    @Test
    void selectFileWithNoAvailableFileMatching() {
        OffsetDateTime now = OffsetDateTime.now();
        Task task = new Task(now);
        task.setStatus(TaskStatus.READY);
        Mockito.when(taskRepository.findByTimestamp(now)).thenReturn(Optional.of(task));

        Assertions.assertThatThrownBy(() -> fileSelectorService.selectFile(now, "CRAC", "no-file-matching"))
                .isInstanceOf(ProcessFileNotFoundException.class);
    }

    @Test
    void selectFileWithStatusReset() {
        OffsetDateTime now = OffsetDateTime.now();
        ProcessFile processFile1 = new ProcessFile("path/to/crac-file", "input", "CRAC", now, now, now);
        ProcessFile processFile2 = new ProcessFile("path/to/other-crac-file", "input", "CRAC", now, now, now);
        Task task = new Task(now);
        task.setStatus(TaskStatus.SUCCESS);
        task.addProcessFile(processFile1);
        task.addProcessFile(processFile2);
        Mockito.when(taskRepository.findByTimestamp(now)).thenReturn(Optional.of(task));
        Mockito.when(taskRepository.save(task)).thenReturn(task);
        Mockito.doNothing().when(taskUpdateNotifier).notify(task, false, false);

        Assertions.assertThat(task.getInput("CRAC")).contains(processFile2);

        fileSelectorService.selectFile(now, "CRAC", "crac-file");

        Assertions.assertThat(task.getInput("CRAC")).contains(processFile1);
        Assertions.assertThat(task.getStatus()).isEqualTo(TaskStatus.READY);
    }

    @Test
    void selectFileWithoutStatusReset() {
        OffsetDateTime now = OffsetDateTime.now();
        ProcessFile processFile1 = new ProcessFile("path/to/crac-file", "input", "CRAC", now, now, now);
        ProcessFile processFile2 = new ProcessFile("path/to/other-crac-file", "input", "CRAC", now, now, now);
        Task task = new Task(now);
        task.setStatus(TaskStatus.READY);
        task.addProcessFile(processFile1);
        task.addProcessFile(processFile2);
        Mockito.when(taskRepository.findByTimestamp(now)).thenReturn(Optional.of(task));
        Mockito.when(taskRepository.save(task)).thenReturn(task);
        Mockito.doNothing().when(taskUpdateNotifier).notify(task, false, false);

        Assertions.assertThat(task.getInput("CRAC")).contains(processFile2);

        fileSelectorService.selectFile(now, "CRAC", "crac-file");

        Assertions.assertThat(task.getInput("CRAC")).contains(processFile1);
    }
}
