/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessEventDto;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Entity
public class ProcessEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "level")
    private String level;

    @Column(name = "timestamp")
    private OffsetDateTime timestamp;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    public ProcessEvent() {
    }

    public ProcessEvent(Task task, OffsetDateTime timestamp, String level, String message) {
        this.id = UUID.randomUUID();
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.task = task;
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

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public static ProcessEventDto createDtoFromEntity(ProcessEvent processEvent) {
        return new ProcessEventDto(processEvent.getTimestamp(),
                processEvent.getLevel(),
                processEvent.getMessage());
    }
}
