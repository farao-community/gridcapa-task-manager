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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class TaskDto {
    private final UUID id;
    private final OffsetDateTime timestamp;
    private final TaskStatus status;
    private final List<ProcessFileDto> inputs;
    private final List<ProcessFileDto> availableInputs;
    private final List<ProcessFileDto> outputs;
    private final List<ProcessEventDto> processEvents;
    private final List<TaskParameterDto> parameters;

    @JsonCreator
    public TaskDto(@JsonProperty("id") UUID id,
                   @JsonProperty("timestamp") OffsetDateTime timestamp,
                   @JsonProperty("status") TaskStatus status,
                   @JsonProperty("inputs") List<ProcessFileDto> inputs,
                   @JsonProperty("availableInputs") List<ProcessFileDto> availableInputs,
                   @JsonProperty("outputs") List<ProcessFileDto> outputs,
                   @JsonProperty("processEvents") List<ProcessEventDto> processEvents,
                   @JsonProperty("parameters") List<TaskParameterDto> parameters) {
        this.id = id;
        this.timestamp = timestamp;
        this.status = status;
        this.inputs = inputs;
        this.availableInputs = availableInputs;
        this.outputs = outputs;
        this.processEvents = processEvents;
        this.parameters = parameters;
    }

    public static TaskDto emptyTask(OffsetDateTime timestamp,
                                    List<String> inputs,
                                    List<String> outputs) {
        return new TaskDto(
                UUID.randomUUID(),
                timestamp,
                TaskStatus.NOT_CREATED,
                inputs.stream().map(ProcessFileDto::emptyProcessFile).toList(),
                List.of(),
                outputs.stream().map(ProcessFileDto::emptyProcessFile).toList(),
                new ArrayList<>(),
                new ArrayList<>());
    }

    public UUID getId() {
        return id;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public List<ProcessFileDto> getInputs() {
        return inputs;
    }

    public List<ProcessFileDto> getOutputs() {
        return outputs;
    }

    public List<ProcessFileDto> getAvailableInputs() {
        return availableInputs;
    }

    public List<ProcessEventDto> getProcessEvents() {
        return processEvents;
    }

    public List<TaskParameterDto> getParameters() {
        return parameters;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
