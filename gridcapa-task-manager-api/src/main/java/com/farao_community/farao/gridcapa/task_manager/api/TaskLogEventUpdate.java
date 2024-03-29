/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Optional;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class TaskLogEventUpdate {
    private final String id;
    private final String level;
    private final String timestamp;
    private final String message;
    private final String serviceName;
    private final String eventPrefix;

    @JsonCreator
    public TaskLogEventUpdate(@JsonProperty("gridcapa-task-id") String id, @JsonProperty("timestamp") String timestamp, @JsonProperty("level") String level, @JsonProperty("message") String message, @JsonProperty("serviceName") String serviceName, @JsonProperty(value = "eventPrefix") String eventPrefix) {
        this.id = id;
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.serviceName = serviceName;
        this.eventPrefix = eventPrefix;
    }

    public TaskLogEventUpdate(@JsonProperty("gridcapa-task-id") String id, @JsonProperty("timestamp") String timestamp, @JsonProperty("level") String level, @JsonProperty("message") String message, @JsonProperty("serviceName") String serviceName) {
        this(id, timestamp, level, message, serviceName, null);
    }

    public String getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Optional<String> getEventPrefix() {
        return Optional.ofNullable(eventPrefix);
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
