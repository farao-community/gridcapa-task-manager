/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import javax.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@Entity
public class ProcessEvent implements Comparable<ProcessEvent>, Serializable {

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
        if (this.timestamp.compareTo(o.getTimestamp()) == 0) {
            return this.message.compareTo(o.getMessage());
        }
        return this.timestamp.compareTo(o.getTimestamp());
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
        return this.id.equals(that.id) && this.level.equals(that.level) && this.timestamp.equals(that.timestamp) && this.message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.level, this.timestamp, this.message);
    }

    @Override
    public String toString() {
        return timestamp + " " + level + " " + message + "\n";
    }
}
