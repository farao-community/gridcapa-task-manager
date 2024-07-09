/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.service.MinioHandler;
import io.minio.messages.Event;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public final class TaskManagerTestUtil {

    private TaskManagerTestUtil() {
    }

    public static Event createEvent(String processTag, String fileGroup, String fileType, String fileKey, String documentId, String validityInterval) {
        Event event = Mockito.mock(Event.class);
        Map<String, String> metadata = new HashMap<>();
        metadata.put(MinioHandler.FILE_GROUP_METADATA_KEY, fileGroup);
        metadata.put(MinioHandler.FILE_TARGET_PROCESS_METADATA_KEY, processTag);
        metadata.put(MinioHandler.FILE_TYPE_METADATA_KEY, fileType);
        metadata.put(MinioHandler.FILE_VALIDITY_INTERVAL_METADATA_KEY, validityInterval);
        if (null != documentId) {
            metadata.put(MinioHandler.DOCUMENT_ID_METADATA_KEY, documentId);
        }
        //The following mock is not use in all test that call this methods. With the "lenient" add you avoid an exception
        Mockito.lenient().when(event.userMetadata()).thenReturn(metadata);
        Mockito.when(event.objectName()).thenReturn(fileKey);
        return event;
    }

    public static Event createEvent(String processTag, String fileGroup, String fileType, String fileKey, String validityInterval) {
        return createEvent(processTag, fileGroup, fileType, fileKey, null, validityInterval);
    }
}
