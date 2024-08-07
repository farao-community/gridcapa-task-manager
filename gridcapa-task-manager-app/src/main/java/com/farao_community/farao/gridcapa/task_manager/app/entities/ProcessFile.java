/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.apache.commons.io.FilenameUtils;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.Comparator;
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

    @Column(name = "file_group")
    private String fileGroup;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "starting_availability_date")
    private OffsetDateTime startingAvailabilityDate;

    @Column(name = "ending_availability_date")
    private OffsetDateTime endingAvailabilityDate;

    @Column(name = "last_modification_date")
    private OffsetDateTime lastModificationDate;

    public ProcessFile() {

    }

    public ProcessFile(String fileObjectKey,
                       String fileGroup,
                       String fileType,
                       String documentId,
                       OffsetDateTime startingAvailabilityDate,
                       OffsetDateTime endingAvailabilityDate,
                       OffsetDateTime lastModificationDate) {
        this.id = UUID.randomUUID();
        this.fileObjectKey = fileObjectKey;
        this.fileGroup = fileGroup;
        this.fileType = fileType;
        this.documentId = documentId;
        this.startingAvailabilityDate = startingAvailabilityDate;
        this.endingAvailabilityDate = endingAvailabilityDate;
        this.lastModificationDate = lastModificationDate;
    }

    public UUID getId() {
        return id;
    }

    public String getFileGroup() {
        return fileGroup;
    }

    public String getFileType() {
        return fileType;
    }

    public String getFilename() {
        return FilenameUtils.getName(fileObjectKey);
    }

    public String getDocumentId() {
        return documentId;
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

    public String getFileObjectKey() {
        return fileObjectKey;
    }

    public void setFileObjectKey(String fileObjectKey) {
        this.fileObjectKey = fileObjectKey;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public boolean isInputFile() {
        return MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE.equals(this.getFileGroup());
    }

    public boolean isOutputFile() {
        return MinioAdapterConstants.DEFAULT_GRIDCAPA_OUTPUT_GROUP_METADATA_VALUE.equals(this.getFileGroup());
    }

    @Override
    public int compareTo(@NotNull ProcessFile otherProcessFile) {
        Comparator<ProcessFile> processFileComparator = Comparator.comparing(ProcessFile::getFileType)
                .thenComparing(ProcessFile::getFileGroup)
                .thenComparing(ProcessFile::getStartingAvailabilityDate);
        if (this.isInputFile()) {
            processFileComparator = processFileComparator.thenComparing(ProcessFile::getLastModificationDate);
        }
        return processFileComparator.compare(this, otherProcessFile);
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
        boolean modificationDateEqualityForInputFiles = !this.isInputFile() || Objects.equals(this.lastModificationDate, that.lastModificationDate);
        return Objects.equals(this.fileType, that.fileType) &&
                Objects.equals(this.fileGroup, that.fileGroup) &&
                Objects.equals(this.startingAvailabilityDate, that.startingAvailabilityDate) &&
                modificationDateEqualityForInputFiles;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.lastModificationDate, this.fileType, this.fileGroup, this.startingAvailabilityDate);
    }
}
