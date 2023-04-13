package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author arnouldjpi
 */
@SpringBootTest(properties = {"purge-task-events.nb-days=7", "purge-task-events.cron=0 0 0 * * *"})
public class DatabasePurgeServiceTest {

    @Autowired
    private TaskRepository taskRepository;

    @MockBean
    private StreamBridge streamBridge; // Useful to avoid AMQP connection that would fail

    @Autowired
    private DatabasePurgeService databasePurgeService;

    @Autowired
    private EventHandler eventHandler;


    @Test
    void scheduledDatabaseTaskEventsPurgeTest() {
        OffsetDateTime offsetDateTimeNow = OffsetDateTime.now(ZoneId.of("UTC"));
        OffsetDateTime offsetDateTimeTask1 = offsetDateTimeNow.minusDays(11);
        OffsetDateTime offsetDateTimeTask2 = offsetDateTimeNow.minusDays(9);
        OffsetDateTime offsetDateTimeEvent11 = offsetDateTimeNow.minusDays(10);
        OffsetDateTime offsetDateTimeEvent12 = offsetDateTimeNow.minusDays(9);
        OffsetDateTime offsetDateTimeEvent21 = offsetDateTimeNow.minusDays(8);
        OffsetDateTime offsetDateTimeEvent22 = offsetDateTimeNow.minusDays(6);

        Task task1 = new Task(offsetDateTimeTask1);
        task1.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f1"));
        taskRepository.save(task1);
        Task task2 = new Task(offsetDateTimeTask2);
        task2.setId(UUID.fromString("1fdda469-53e9-4d63-a533-b935cffdd2f2"));
        taskRepository.save(task2);

        String logEvent11 = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f1\",\n" +
                "  \"timestamp\": \"" + offsetDateTimeEvent11.toString() + "\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend11\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        eventHandler.handleTaskEventUpdate(logEvent11);
        String logEvent12 = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f1\",\n" +
                "  \"timestamp\": \"" + offsetDateTimeEvent12.toString() + "\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend12\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        eventHandler.handleTaskEventUpdate(logEvent12);
        String logEvent21 = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f2\",\n" +
                "  \"timestamp\": \"" + offsetDateTimeEvent21.toString() + "\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend21\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        eventHandler.handleTaskEventUpdate(logEvent21);
        String logEvent22 = "{\n" +
                "  \"gridcapa-task-id\": \"1fdda469-53e9-4d63-a533-b935cffdd2f2\",\n" +
                "  \"timestamp\": \"" + offsetDateTimeEvent22.toString() + "\",\n" +
                "  \"level\": \"INFO\",\n" +
                "  \"message\": \"Hello from backend22\",\n" +
                "  \"serviceName\": \"GRIDCAPA\" \n" +
                "}";
        eventHandler.handleTaskEventUpdate(logEvent22);

        assertEquals(2, taskRepository.findByTimestamp(offsetDateTimeTask1).orElseThrow().getProcessEvents().size());
        assertEquals(2, taskRepository.findByTimestamp(offsetDateTimeTask2).orElseThrow().getProcessEvents().size());

        databasePurgeService.scheduledDatabaseTaskEventsPurge();

        assertEquals(0, taskRepository.findByTimestamp(offsetDateTimeTask1).orElseThrow().getProcessEvents().size());
        assertEquals(1, taskRepository.findByTimestamp(offsetDateTimeTask2).orElseThrow().getProcessEvents().size());
        assertEquals(1, taskRepository.findAllWithSomeProcessEvent().size());
    }
}
