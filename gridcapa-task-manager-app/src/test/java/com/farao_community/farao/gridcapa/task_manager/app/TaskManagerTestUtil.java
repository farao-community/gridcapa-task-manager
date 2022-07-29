/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import io.minio.messages.Event;
import org.mockito.Mockito;

import java.util.Map;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public final class TaskManagerTestUtil {

    private TaskManagerTestUtil() {
    }

    public static Event createEvent(MinioAdapter minioAdapter, String processTag, String fileGroup, String fileType, String fileKey, String validityInterval, String fileUrl) {
        Event event = Mockito.mock(Event.class);
        Map<String, String> metadata = Map.of(
            TaskManager.FILE_GROUP_METADATA_KEY, fileGroup,
            TaskManager.FILE_TARGET_PROCESS_METADATA_KEY, processTag,
            TaskManager.FILE_TYPE_METADATA_KEY, fileType,
            TaskManager.FILE_VALIDITY_INTERVAL_METADATA_KEY, validityInterval
        );
        //The following mock is not use in all test that call this methods. With the "lenient" add you avoid an exception
        Mockito.lenient().when(event.userMetadata()).thenReturn(metadata);
        Mockito.when(event.objectName()).thenReturn(fileKey);
        Mockito.when(minioAdapter.generatePreSignedUrl(event.objectName())).thenReturn(fileUrl);
        return event;
    }
}
