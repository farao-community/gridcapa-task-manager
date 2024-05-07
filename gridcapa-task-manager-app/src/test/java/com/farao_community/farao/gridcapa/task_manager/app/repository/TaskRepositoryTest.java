package com.farao_community.farao.gridcapa.task_manager.app.repository;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.Optional;

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
}
