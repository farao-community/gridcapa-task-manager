/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Entity
public class ProcessFile {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "filename")
    private String filename;

    @Column(name = "last_modification_date")
    private OffsetDateTime lastModificationDate;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "file_object_key", columnDefinition = "TEXT")
    private String fileObjectKey;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinTable(
        name = "process_file_task",
        joinColumns = @JoinColumn(name = "fk_process_file"),
        inverseJoinColumns = @JoinColumn(name = "fk_task"))
    private Set<Task> tasks = new HashSet<>();

    public ProcessFile() {

    }

    public ProcessFile(String fileType) {
        this.id = UUID.randomUUID();
        this.fileType = fileType;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public OffsetDateTime getLastModificationDate() {
        return lastModificationDate;
    }

    public void setLastModificationDate(OffsetDateTime lastModificationDate) {
        this.lastModificationDate = lastModificationDate;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileObjectKey() {
        return fileObjectKey;
    }

    public void setFileObjectKey(String fileObjectKey) {
        this.fileObjectKey = fileObjectKey;
    }

    public Set<Task> getTasks() {
        return tasks;
    }

    public void setTasks(Set<Task> tasks) {
        this.tasks = tasks;
    }

    public void addTask(Task task) {
        task.getProcessFiles().add(this);
        tasks.add(task);
    }

    public void removeTask(Task task) {
        task.getProcessFiles().remove(this);
        tasks.remove(task);
    }

    public static ProcessFileDto createDtofromEntity(ProcessFile processFile) {
        return new ProcessFileDto(
            processFile.getFileType(),
            ProcessFileStatus.VALIDATED,
            processFile.getFilename(),
            processFile.getLastModificationDate(),
            processFile.getFileUrl());
    }
}
