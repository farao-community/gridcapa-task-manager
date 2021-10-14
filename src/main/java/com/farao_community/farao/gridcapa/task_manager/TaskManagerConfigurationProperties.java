/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.util.List;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@ConstructorBinding
@ConfigurationProperties("task-server")
public class TaskManagerConfigurationProperties {

    private final MinIoProperties minio;
    private final ProcessProperties process;

    public TaskManagerConfigurationProperties(MinIoProperties minio, ProcessProperties process) {
        this.minio = minio;
        this.process = process;
    }

    public MinIoProperties getMinio() {
        return minio;
    }

    public ProcessProperties getProcess() {
        return process;
    }

    public static final class ProcessProperties {
        private final String tag;
        private final String timezone;
        private final List<String> inputs;

        private ProcessProperties(String tag, String timezone, List<String> inputs) {
            this.tag = tag;
            this.timezone = timezone;
            this.inputs = inputs;
        }

        public String getTag() {
            return tag;
        }

        public String getTimezone() {
            return timezone;
        }

        public List<String> getInputs() {
            return inputs;
        }
    }

    public static final class MinIoProperties {
        private final ConnectionProperties connect;
        private final NotificationProperties notification;

        private MinIoProperties(ConnectionProperties connect, NotificationProperties notification) {
            this.connect = connect;
            this.notification = notification;
        }

        public ConnectionProperties getConnect() {
            return connect;
        }

        public NotificationProperties getNotification() {
            return notification;
        }

        public static final class ConnectionProperties {
            private final String url;
            private final String accessKey;
            private final String secretKey;

            private ConnectionProperties(String url, String accessKey, String secretKey) {
                this.url = url;
                this.accessKey = accessKey;
                this.secretKey = secretKey;
            }

            public String getUrl() {
                return url;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public String getSecretKey() {
                return secretKey;
            }
        }

        public static final class NotificationProperties {
            private final String exchange;
            private final String queue;

            private NotificationProperties(String exchange, String queue) {
                this.exchange = exchange;
                this.queue = queue;
            }

            public String getExchange() {
                return exchange;
            }

            public String getQueue() {
                return queue;
            }
        }
    }
}
