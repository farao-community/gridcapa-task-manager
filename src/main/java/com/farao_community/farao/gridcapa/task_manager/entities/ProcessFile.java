/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.entities;

import javax.persistence.*;
import java.time.LocalDateTime;
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

    @Column(name = "status")
    private ProcessFileStatus processFileStatus;

    @Column(name = "filename")
    private String filename;

    @Column(name = "last_modification_date")
    private LocalDateTime lastModificationDate;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    public ProcessFile() {

    }

    public ProcessFile(Task task, String fileType) {
        this.id = UUID.randomUUID();
        this.fileType = fileType;
        this.processFileStatus = ProcessFileStatus.NOT_PRESENT;
        this.task = task;
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

    public ProcessFileStatus getProcessFileStatus() {
        return processFileStatus;
    }

    public void setProcessFileStatus(ProcessFileStatus processFileStatus) {
        this.processFileStatus = processFileStatus;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public LocalDateTime getLastModificationDate() {
        return lastModificationDate;
    }

    public void setLastModificationDate(LocalDateTime lastModificationDate) {
        this.lastModificationDate = lastModificationDate;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
