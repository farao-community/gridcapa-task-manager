package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
class TaskWithStatusUpdateTest {

    @Test
    void testEqualsAndHashCode() {
        Task task1 = new Task(OffsetDateTime.now());
        Task task2 = new Task(OffsetDateTime.now().plusSeconds(1));

        TaskWithStatusUpdate obj1 = new TaskWithStatusUpdate(task1, true);
        TaskWithStatusUpdate obj1Copy = new TaskWithStatusUpdate(task1, true);
        TaskWithStatusUpdate obj2 = new TaskWithStatusUpdate(task2, true);
        TaskWithStatusUpdate obj3 = Mockito.mock(TaskWithStatusUpdate.class);

        // Testing equals()
        assertEquals(obj1, obj1);    // Test reflexive property
        assertEquals(obj1, obj1Copy); // Test symmetric property
        assertEquals(obj1Copy, obj1); // Test symmetric property
        assertNotEquals(obj1, obj2);    // Test objects with different state
        assertNotEquals(null, obj1);    // Test against null
        assertNotEquals(obj1, obj3);    // Test against different class
    }
}
