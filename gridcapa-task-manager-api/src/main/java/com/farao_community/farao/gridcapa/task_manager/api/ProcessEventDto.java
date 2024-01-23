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

import java.time.OffsetDateTime;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class ProcessEventDto {
    private String level;
    private OffsetDateTime timestamp;
    private String message;
    private String serviceName;

    @JsonCreator
    public ProcessEventDto(@JsonProperty("timestamp") OffsetDateTime timestamp,
                           @JsonProperty("level") String level,
                           @JsonProperty("message") String message,
                           @JsonProperty("serviceName") String serviceName) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.serviceName = serviceName;
    }

    public OffsetDateTime getTimestamp() {
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

    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
