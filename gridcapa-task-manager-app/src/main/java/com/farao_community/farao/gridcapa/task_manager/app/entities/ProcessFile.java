/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.FileGroup;
import org.apache.commons.io.FilenameUtils;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Entity
@org.hibernate.annotations.Cache(
    usage = CacheConcurrencyStrategy.READ_WRITE
)
@NaturalIdCache
public class ProcessFile implements Comparable<ProcessFile> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NaturalId(mutable = true)
    @Column(name = "file_object_key", length = 500, nullable = false, unique = true)
    private String fileObjectKey;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_group")
    private FileGroup fileGroup;

    @Column(name = "starting_availability_date")
    private OffsetDateTime startingAvailabilityDate;

    @Column(name = "ending_availability_date")
    private OffsetDateTime endingAvailabilityDate;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "last_modification_date")
    private OffsetDateTime lastModificationDate;

    public ProcessFile() {

    }

    public ProcessFile(String fileObjectKey,
                       String fileType,
                       FileGroup fileGroup,
                       OffsetDateTime startingAvailabilityDate,
                       OffsetDateTime endingAvailabilityDate,
                       String fileUrl,
                       OffsetDateTime lastModificationDate) {
        this.id = UUID.randomUUID();
        this.fileObjectKey = fileObjectKey;
        this.fileType = fileType;
        this.fileGroup = fileGroup;
        this.startingAvailabilityDate = startingAvailabilityDate;
        this.endingAvailabilityDate = endingAvailabilityDate;
        this.fileUrl = fileUrl;
        this.lastModificationDate = lastModificationDate;
    }

    public UUID getId() {
        return id;
    }

    public String getFileType() {
        return fileType;
    }

    public FileGroup getFileGroup() {
        return fileGroup;
    }

    public String getFilename() {
        return FilenameUtils.getName(fileObjectKey);
    }

    public OffsetDateTime getStartingAvailabilityDate() {
        return startingAvailabilityDate;
    }

    public OffsetDateTime getEndingAvailabilityDate() {
        return endingAvailabilityDate;
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

    @Override
    public int compareTo(ProcessFile o) {
        return fileType.compareTo(o.getFileType());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProcessFile that = (ProcessFile) o;
        return Objects.equals(id, that.id) && Objects.equals(fileType, that.fileType) && Objects.equals(lastModificationDate, that.lastModificationDate) && Objects.equals(fileObjectKey, that.fileObjectKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fileType, lastModificationDate, fileObjectKey);
    }
}
