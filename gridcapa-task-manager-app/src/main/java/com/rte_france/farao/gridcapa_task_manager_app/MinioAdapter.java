/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.farao.gridcapa_task_manager_app;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import io.minio.messages.Event;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Component
public class MinioAdapter {
    private static final int DEFAULT_DOWNLOAD_LINK_EXPIRY_IN_DAYS = 7;

    private final MinioClient minioClient;

    public MinioAdapter(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String generatePreSignedUrl(Event event) {
        try {
            String objectName = URLDecoder.decode(event.objectName(), StandardCharsets.UTF_8);
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(event.bucketName())
                            .object(objectName)
                            .expiry(DEFAULT_DOWNLOAD_LINK_EXPIRY_IN_DAYS, TimeUnit.DAYS).method(Method.GET).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
