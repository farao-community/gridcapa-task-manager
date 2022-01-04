/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class TaskLogEventUpdate {
    private final String id;
    private final String level;
    private final String timestamp;
    private final String message;
    private final String serviceName;

    @JsonCreator
    public TaskLogEventUpdate(@JsonProperty("gridcapa-task-id") String id, @JsonProperty("timestamp") String timestamp, @JsonProperty("level") String level, @JsonProperty("message") String message, @JsonProperty("serviceName") String serviceName) {
        this.id = id;
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.serviceName = serviceName;
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
}
