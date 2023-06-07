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
        assertEquals(obj1, obj1);
        assertEquals(obj1, obj1Copy);
        assertEquals(obj1Copy, obj1);
        assertNotEquals(obj1, obj2);
        assertNotEquals(null, obj1);
        assertNotEquals(obj1, obj3);
    }
}
