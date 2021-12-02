/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class TaskDto {
    private final UUID id;
    private final LocalDateTime timestamp;
    private final TaskStatus status;
    private final List<ProcessFileDto> processFiles;

    @JsonCreator
    public TaskDto(@JsonProperty("id") UUID id,
                   @JsonProperty("timestamp") LocalDateTime timestamp,
                   @JsonProperty("status") TaskStatus status,
                   @JsonProperty("processFiles") List<ProcessFileDto> processFiles) {
        this.id = id;
        this.timestamp = timestamp;
        this.status = status;
        this.processFiles = processFiles;
    }

    public static TaskDto emptyTask(LocalDateTime timestamp, List<String> fileTypes) {
        return new TaskDto(
                UUID.randomUUID(),
                timestamp,
                TaskStatus.NOT_CREATED,
                fileTypes.stream().map(ProcessFileDto::emptyProcessFile).collect(Collectors.toList()));
    }

    public UUID getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public List<ProcessFileDto> getProcessFiles() {
        return processFiles;
    }
}
