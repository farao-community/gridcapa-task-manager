package com.farao_community.farao.gridcapa.task_manager.app.repository;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

@SpringBootTest
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void testTimestampPersistence() {
        //2024 Summer > Winter daylight saving time
        final OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-10-27T01:30Z");
        taskRepository.save(new Task(offsetDateTime));
        final Optional<Task> persistedTask = taskRepository.findByTimestamp(offsetDateTime);
        Assertions.assertTrue(persistedTask.isPresent());
        Assertions.assertEquals(offsetDateTime, persistedTask.get().getTimestamp());
    }

    @Test
    void findTaskStatusByTimestampBetweenTest() {
        final OffsetDateTime offsetDateTimeBegin = OffsetDateTime.parse("2025-10-25T22:00Z");
        final OffsetDateTime offsetDateTimeMiddle = OffsetDateTime.parse("2025-10-26T12:30Z");
        final OffsetDateTime offsetDateTimeEnd = OffsetDateTime.parse("2025-10-26T22:30Z");
        final Set<TaskStatus> emptyResult = taskRepository.findTaskStatusByTimestampBetween(offsetDateTimeBegin, offsetDateTimeEnd);
        org.assertj.core.api.Assertions.assertThat(emptyResult)
                .isNotNull()
                .isEmpty();
        final OffsetDateTime offsetDateTimeBefore = OffsetDateTime.parse("2025-10-25T21:59Z");
        final OffsetDateTime offsetDateTimeAfter = OffsetDateTime.parse("2025-10-26T22:31Z");
        taskRepository.save(new Task(offsetDateTimeBefore));
        taskRepository.save(new Task(offsetDateTimeEnd));
        taskRepository.save(new Task(offsetDateTimeBegin));
        taskRepository.save(new Task(offsetDateTimeAfter));
        final Set<TaskStatus> result = taskRepository.findTaskStatusByTimestampBetween(offsetDateTimeBegin, offsetDateTimeEnd);
        org.assertj.core.api.Assertions.assertThat(result)
                .isNotNull()
                .isNotEmpty()
                .hasSize(1);

        final Task middle = new Task(offsetDateTimeMiddle);
        middle.setStatus(TaskStatus.SUCCESS);
        taskRepository.save(middle);
        final Set<TaskStatus> result2 = taskRepository.findTaskStatusByTimestampBetween(offsetDateTimeBegin, offsetDateTimeEnd);
        org.assertj.core.api.Assertions.assertThat(result2)
                .isNotNull()
                .isNotEmpty()
                .hasSize(2);

    }
}
