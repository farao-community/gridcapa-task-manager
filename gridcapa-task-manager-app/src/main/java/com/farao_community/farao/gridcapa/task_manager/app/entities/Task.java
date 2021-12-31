/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;
import org.hibernate.annotations.SortNatural;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Entity
@org.hibernate.annotations.Cache(
    usage = CacheConcurrencyStrategy.READ_WRITE
)
@NaturalIdCache
public class Task {

    @Id
    @Column(name = "id")
    private UUID id;

    @NaturalId
    @Column(name = "timestamp", nullable = false, updatable = false, unique = true)
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

    @ManyToMany(cascade = { CascadeType.MERGE, CascadeType.PERSIST })
    @JoinTable(
        name = "task_process_file",
        joinColumns = @JoinColumn(name = "fk_task"),
        inverseJoinColumns = @JoinColumn(name = "fk_process_file"))
    @SortNatural
    private SortedSet<ProcessFile> processFiles = new TreeSet<>();

    public Task() {

    }

    public Task(OffsetDateTime timestamp) {
        this.id = UUID.randomUUID();
        this.timestamp = timestamp;
        status = TaskStatus.CREATED;
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

    public SortedSet<ProcessFile> getProcessFiles() {
        return processFiles;
    }

    public void setProcessFiles(SortedSet<ProcessFile> processFiles) {
        this.processFiles = processFiles;
    }

    public void addProcessFile(ProcessFile processFile) {
        getProcessFiles().add(processFile);
    }

    public void removeProcessFile(ProcessFile processFile) {
        getProcessFiles().remove(processFile);
    }

    public Optional<ProcessFile> getProcessFile(String fileType) {
        return processFiles.stream()
            .filter(file -> file.getFileType().equals(fileType))
            .findFirst();
    }

    public static TaskDto createDtoFromEntity(Task task, List<String> inputs) {
        return new TaskDto(
            task.getId(),
            task.getTimestamp(),
            task.getStatus(),
            inputs.stream()
                .map(input -> task.getProcessFile(input)
                    .map(ProcessFile::createDtofromEntity)
                    .orElseGet(() -> ProcessFileDto.emptyProcessFile(input)))
                .collect(Collectors.toList()),
            task.getProcessEvents().stream().map(ProcessEvent::createDtoFromEntity).collect(Collectors.toList()));
    }
}
