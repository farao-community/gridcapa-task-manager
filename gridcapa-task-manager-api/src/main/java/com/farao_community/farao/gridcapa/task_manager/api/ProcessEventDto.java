package com.farao_community.farao.gridcapa.task_manager.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ProcessEventDto {

    private String level;
    private LocalDateTime timestamp;
    private String message;

    @JsonCreator
    public ProcessEventDto(@JsonProperty LocalDateTime timestamp, @JsonProperty String level, @JsonProperty String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
