/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.messages.Event;
import io.minio.messages.NotificationRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@Component
public class MinioNotificationsListener implements MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinioNotificationsListener.class);
    private static final ObjectMapper EVENT_NOTIFICATION_MAPPER = initializeObjectMapper();

    private final TaskManager taskManager;

    public MinioNotificationsListener(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    private static ObjectMapper initializeObjectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void onMessage(Message message) {
        NotificationRecords notificationRecords = parseNotificationRecords(message);
        if (notificationRecords != null) {
            handleNotification(notificationRecords);
        }
    }

    private @Nullable NotificationRecords parseNotificationRecords(Message message) {
        try {
            return EVENT_NOTIFICATION_MAPPER.readValue(message.getBody(), NotificationRecords.class);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    private void handleNotification(NotificationRecords notificationRecords) {
        notificationRecords.events().forEach(this::handleEvent);
    }

    private void handleEvent(Event event) {
        switch (event.eventType()) {
            case OBJECT_CREATED_ANY:
            case OBJECT_CREATED_PUT:
            case OBJECT_CREATED_POST:
            case OBJECT_CREATED_COPY:
            case OBJECT_CREATED_COMPLETE_MULTIPART_UPLOAD:
                handleObjectCreationEvent(event);
                break;
            case OBJECT_REMOVED_ANY:
            case OBJECT_REMOVED_DELETE:
            case OBJECT_REMOVED_DELETED_MARKER_CREATED:
                handleObjectRemovalEvent(event);
                break;
            default:
                LOGGER.info("S3 event type {} not handled by task manager", event.eventType());
                break;
        }
    }

    private void handleObjectCreationEvent(Event event) {
        taskManager.updateTasks(event);
    }

    private void handleObjectRemovalEvent(Event event) {
        taskManager.removeProcessFile(event);
    }
}
