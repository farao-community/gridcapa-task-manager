/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalIdCache;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Entity
@org.hibernate.annotations.Cache(
        usage = CacheConcurrencyStrategy.READ_WRITE
)
@NaturalIdCache
public class ProcessRun {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "execution_date")
    private OffsetDateTime executionDate;

    @ManyToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST}, fetch = FetchType.EAGER)
    @JoinTable(
            name = "process_run_process_file",
            joinColumns = @JoinColumn(name = "fk_process_run"),
            inverseJoinColumns = @JoinColumn(name = "fk_process_file"))
    private final List<ProcessFile> inputFiles = new ArrayList<>();

    public ProcessRun() {

    }

    public ProcessRun(final Collection<ProcessFile> inputFiles) {
        this.id = UUID.randomUUID();
        this.executionDate = OffsetDateTime.now();
        this.inputFiles.addAll(inputFiles);
    }

    public UUID getId() {
        return id;
    }

    public OffsetDateTime getExecutionDate() {
        return executionDate;
    }

    public List<ProcessFile> getInputFiles() {
        return inputFiles;
    }

    public void removeInputFileByFilename(String filename) {
        this.inputFiles.removeIf(file -> file.getFilename().equals(filename));
    }
}
