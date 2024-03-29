/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.comparators.ReverseEventComparator;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;
import org.hibernate.annotations.SortNatural;
import org.hibernate.annotations.SortComparator;

import javax.persistence.*;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Entity
@org.hibernate.annotations.Cache(
    usage = CacheConcurrencyStrategy.READ_WRITE
)
@NaturalIdCache
@Table(indexes = { @Index(columnList = "status", name = "task_status_idx") })
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
        cascade = {CascadeType.MERGE, CascadeType.PERSIST},
        orphanRemoval = true
    )
    @SortComparator(ReverseEventComparator.class)
    private final SortedSet<ProcessEvent> processEvents = Collections.synchronizedSortedSet(new TreeSet<>());

    @ManyToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinTable(
        name = "task_process_file",
        joinColumns = @JoinColumn(name = "fk_task"),
        inverseJoinColumns = @JoinColumn(name = "fk_process_file"))
    @SortNatural
    private final SortedSet<ProcessFile> processFiles = new TreeSet<>();

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

    public SortedSet<ProcessEvent> getProcessEvents() {
        return processEvents;
    }

    public void addProcessEvent(OffsetDateTime timestamp, String level, String message, String serviceName) {
        processEvents.add(new ProcessEvent(this, timestamp, level, message, serviceName));
    }

    public SortedSet<ProcessFile> getProcessFiles() {
        return processFiles;
    }

    public void addProcessFile(String fileObjectKey,
                               String fileGroup,
                               String fileType,
                               OffsetDateTime startingAvailabilityDate,
                               OffsetDateTime endingAvailabilityDate,
                               OffsetDateTime lastModificationDate) {
        addProcessFile(new ProcessFile(fileObjectKey, fileGroup, fileType, startingAvailabilityDate, endingAvailabilityDate, lastModificationDate));
    }

    public void addProcessFile(ProcessFile processFile) {
        processFiles.add(processFile);
    }

    public void removeProcessFile(ProcessFile processFile) {
        processFiles.remove(processFile);
    }

    public Optional<ProcessFile> getInput(String fileType) {
        return processFiles.stream()
            .filter(file -> MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(file.getFileGroup()))
            .filter(file -> fileType.equals(file.getFileType()))
            .max(Comparator.comparing(ProcessFile::getStartingAvailabilityDate));
    }

    public Optional<ProcessFile> getOutput(String fileType) {
        return processFiles.stream()
            .filter(file -> MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE.equals(file.getFileGroup()))
            .filter(file -> fileType.equals(file.getFileType()))
            .findFirst();
    }
}
