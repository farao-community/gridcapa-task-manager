/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa.task_manager.app.entities.comparators.ReverseEventComparator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;
import org.hibernate.annotations.SortComparator;
import org.hibernate.annotations.SortNatural;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Entity
@org.hibernate.annotations.Cache(
        usage = CacheConcurrencyStrategy.READ_WRITE
)
@NaturalIdCache
@Table(indexes = {@Index(columnList = "status", name = "task_status_idx")})
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

    @ManyToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST}, fetch = FetchType.EAGER)
    @JoinTable(
            name = "task_available_process_file",
            joinColumns = @JoinColumn(name = "fk_task"),
            inverseJoinColumns = @JoinColumn(name = "fk_process_file"))
    @SortNatural
    private final SortedSet<ProcessFile> availableInputProcessFiles = new TreeSet<>();

    @OneToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST}, fetch = FetchType.EAGER)
    private final List<ProcessRun> runHistory = new ArrayList<>();

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

    public void addProcessFile(ProcessFile processFile) {
        if (processFile.isInputFile()) {
            availableInputProcessFiles.removeIf(pf -> pf.getFileObjectKey().equals(processFile.getFileObjectKey()));
            availableInputProcessFiles.add(processFile);
            selectProcessFile(processFile);
        } else {
            processFiles.add(processFile);
        }
    }

    public FileRemovalStatus removeProcessFile(ProcessFile processFile) {
        final boolean fileWasSelected = processFiles.remove(processFile);
        boolean fileWasRemoved = fileWasSelected;

        if (processFile.isInputFile()) {
            fileWasRemoved = availableInputProcessFiles.remove(processFile);

            if (fileWasSelected) {
                availableInputProcessFiles.stream()
                        .filter(pf -> pf.getFileType().equals(processFile.getFileType()))
                        .max(Comparator.comparing(ProcessFile::getLastModificationDate))
                        .ifPresent(this::selectProcessFile);
            }
        }
        return new FileRemovalStatus(fileWasRemoved, fileWasSelected);
    }

    public void selectProcessFile(ProcessFile processFile) {
        processFiles.removeIf(pf -> pf.getFileType().equals(processFile.getFileType()));
        processFiles.add(processFile);
    }

    public Optional<ProcessFile> getInput(String fileType) {
        return processFiles.stream()
                .filter(ProcessFile::isInputFile)
                .filter(file -> fileType.equals(file.getFileType()))
                .max(Comparator.comparing(ProcessFile::getStartingAvailabilityDate));
    }

    public Set<ProcessFile> getAvailableInputs(String fileType) {
        return availableInputProcessFiles.stream()
                .filter(file -> fileType.equals(file.getFileType()))
                .collect(Collectors.toSet());
    }

    public Optional<ProcessFile> getOutput(String fileType) {
        return processFiles.stream()
                .filter(ProcessFile::isOutputFile)
                .filter(file -> fileType.equals(file.getFileType()))
                .findFirst();
    }

    public List<ProcessRun> getRunHistory() {
        return runHistory;
    }

    public void addProcessRun(ProcessRun processRun) {
        runHistory.add(processRun);
    }
}
