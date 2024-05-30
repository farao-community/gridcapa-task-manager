/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
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
import java.util.List;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
public class ProcessRunDto {
    private final OffsetDateTime executionDate;
    private final List<ProcessFileDto> inputs;

    @JsonCreator
    public ProcessRunDto(@JsonProperty("executionDate") OffsetDateTime executionDate,
                         @JsonProperty("inputFiles") List<ProcessFileDto> inputFiles) {
        this.executionDate = executionDate;
        this.inputs = inputFiles;
    }

    public OffsetDateTime getExecutionDate() {
        return executionDate;
    }

    public List<ProcessFileDto> getInputs() {
        return inputs;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
