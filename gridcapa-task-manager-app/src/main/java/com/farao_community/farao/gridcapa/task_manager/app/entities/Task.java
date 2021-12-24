/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Entity
public class Task {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "timestamp")
    private OffsetDateTime timestamp;

    @Column(name = "status")
    private TaskStatus status;

    @OneToMany(
        mappedBy = "task",
        fetch = FetchType.EAGER,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderColumn
    private List<ProcessEvent> processEvents = new ArrayList<>();

    @OneToMany(
        mappedBy = "task",
        fetch = FetchType.EAGER,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderColumn
    private List<ProcessFile> processFiles = new ArrayList<>();

    public Task() {

    }

    public Task(OffsetDateTime timestamp, List<String> fileTypes) {
        this.id = UUID.randomUUID();
        this.timestamp = timestamp;
        status = TaskStatus.CREATED;
        fileTypes.forEach(fileType -> processFiles.add(new ProcessFile(this, fileType)));
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public List<ProcessEvent> getProcessEvents() {
        return processEvents;
    }

    public void setProcessEvents(List<ProcessEvent> processFileEvents) {
        this.processEvents = processFileEvents;
    }

    public List<ProcessFile> getProcessFiles() {
        return processFiles;
    }

    public void setProcessFiles(List<ProcessFile> processFiles) {
        this.processFiles = processFiles;
    }

    public ProcessFile getProcessFile(String fileType) {
        return processFiles.stream().filter(file -> file.getFileType().equals(fileType))
            .findFirst()
            .orElseThrow(() -> new RuntimeException(String.format("Queried fileType does not exist %s", fileType)));
    }

    public static TaskDto createDtoFromEntity(Task task) {
        return new TaskDto(
            task.getId(),
            task.getTimestamp(),
            task.getStatus(),
            task.getProcessFiles().stream().map(ProcessFile::createDtofromEntity).collect(Collectors.toList()),
            task.getProcessEvents().stream().map(ProcessEvent::createDtoFromEntity).collect(Collectors.toList()));
    }
}
