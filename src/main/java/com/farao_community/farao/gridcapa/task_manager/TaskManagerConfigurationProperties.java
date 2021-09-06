/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa.task_manager;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@ConstructorBinding
@ConfigurationProperties("task-server.minio")
public class TaskManagerConfigurationProperties {
    private final ConnectionProperties connect;
    private final NotificationProperties notification;

    public TaskManagerConfigurationProperties(ConnectionProperties connect, NotificationProperties notification) {
        this.connect = connect;
        this.notification = notification;
    }

    public ConnectionProperties getConnect() {
        return connect;
    }

    public NotificationProperties getNotification() {
        return notification;
    }

    public static class ConnectionProperties {
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

    public static class NotificationProperties {
        private final String exchange;
        private final String queuePrefix;

        private NotificationProperties(String exchange, String queuePrefix) {
            this.exchange = exchange;
            this.queuePrefix = queuePrefix;
        }

        public String getExchange() {
            return exchange;
        }

        public String getQueuePrefix() {
            return queuePrefix;
        }
    }
}
