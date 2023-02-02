package com.farao_community.farao.gridcapa.task_manager.app;

import io.minio.messages.NotificationRecords;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Flux;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class TaskManagerTestWithMockedRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManagerTestWithMockedRepository.class);

    @MockBean
    TaskManager taskManager;

    @Test
    void testFailedMinioEventHandling() {
        NotificationRecords notificationRecords1 = new NotificationRecords();
        NotificationRecords notificationRecords2 = new NotificationRecords();
        NotificationRecords notificationRecords3 = new NotificationRecords();
        NotificationRecords notificationRecords4 = new NotificationRecords();
        NotificationRecords notificationRecords5 = new NotificationRecords();

        Mockito.when(taskManager.consumeMinioEvent()).thenCallRealMethod();
        Mockito.doAnswer(invocationOnMock -> {
            LOGGER.info("notification1 handled normally");
            return null;
        }).when(taskManager).handleMinioEvent(notificationRecords1);
        Mockito.doAnswer(invocationOnMock -> {
            LOGGER.info("notification2 handled normally");
            return null;
        }).when(taskManager).handleMinioEvent(notificationRecords2);
        Mockito.doThrow(new RuntimeException("error computing notification3")).when(taskManager).handleMinioEvent(notificationRecords3);
        Mockito.doThrow(new RuntimeException("error computing notification4")).when(taskManager).handleMinioEvent(notificationRecords4);
        Mockito.doAnswer(invocationOnMock -> {
            LOGGER.info("notification5 handled normally");
            return null;
        }).when(taskManager).handleMinioEvent(notificationRecords5);

        Flux<NotificationRecords> inputFlux = Flux.just(notificationRecords1, notificationRecords2, notificationRecords3);
        Flux<NotificationRecords> inputFlux2 = Flux.just(notificationRecords4, notificationRecords5);

        taskManager.consumeMinioEvent().accept(inputFlux);
        taskManager.consumeMinioEvent().accept(inputFlux2);
    }

    @Test
    void testFailedTaskEventUpdateHandling() {
        String event1 = "coucou1";
        String event2 = "coucou2";
        String event3 = "coucou3";
        String event4 = "coucou4";
        String event5 = "coucou5";

        Mockito.when(taskManager.consumeTaskEventUpdate()).thenCallRealMethod();
        Mockito.doAnswer(invocationOnMock -> {
            LOGGER.info("notification1 handled normally");
            return null;
        }).when(taskManager).handleTaskEventUpdate(event1);
        Mockito.doAnswer(invocationOnMock -> {
            LOGGER.info("notification2 handled normally");
            return null;
        }).when(taskManager).handleTaskEventUpdate(event2);
        Mockito.doThrow(new RuntimeException("error computing notification3")).when(taskManager).handleTaskEventUpdate(event3);
        Mockito.doThrow(new RuntimeException("error computing notification4")).when(taskManager).handleTaskEventUpdate(event4);
        Mockito.doAnswer(invocationOnMock -> {
            LOGGER.info("notification5 handled normally");
            return null;
        }).when(taskManager).handleTaskEventUpdate(event5);

        Flux<String> inputFlux = Flux.just(event1, event2, event3);
        Flux<String> inputFlux2 = Flux.just(event4, event5);

        taskManager.consumeTaskEventUpdate().accept(inputFlux);
        taskManager.consumeTaskEventUpdate().accept(inputFlux2);
    }
}
