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
import java.util.Objects;
import java.util.UUID;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Entity
public class ProcessEvent implements Comparable<ProcessEvent> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "level")
    private String level;

    @Column(name = "timestamp")
    private OffsetDateTime timestamp;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    public ProcessEvent() {
    }

    public ProcessEvent(OffsetDateTime timestamp, String level, String message) {
        this.id = UUID.randomUUID();
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
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

    @Override
    public int compareTo(ProcessEvent o) {
        return timestamp.compareTo(o.getTimestamp());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProcessEvent that = (ProcessEvent) o;
        return id.equals(that.id) && level.equals(that.level) && timestamp.equals(that.timestamp) && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, timestamp, message);
    }

    public static ProcessEventDto createDtoFromEntity(ProcessEvent processEvent) {
        return new ProcessEventDto(processEvent.getTimestamp(),
                processEvent.getLevel(),
                processEvent.getMessage());
    }
}
