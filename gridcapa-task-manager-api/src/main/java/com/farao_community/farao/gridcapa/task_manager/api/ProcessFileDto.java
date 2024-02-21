/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.time.OffsetDateTime;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class ProcessFileDto {
    private final String filePath;
    private final String fileType;
    private final ProcessFileStatus processFileStatus;
    private final String filename;
    private final OffsetDateTime lastModificationDate;

    @JsonCreator
    public ProcessFileDto(@JsonProperty("filePath") String filePath,
                          @JsonProperty("fileType") String fileType,
                          @JsonProperty("processFileStatus") ProcessFileStatus processFileStatus,
                          @JsonProperty("fileName") String filename,
                          @JsonProperty("lastModificationDate") OffsetDateTime lastModificationDate) {
        this.filePath = filePath;
        this.fileType = fileType;
        this.processFileStatus = processFileStatus;
        this.filename = filename;
        this.lastModificationDate = lastModificationDate;
    }

    public static ProcessFileDto emptyProcessFile(String fileType) {
        return new ProcessFileDto(
                null,
                fileType,
                ProcessFileStatus.NOT_PRESENT,
                null,
                null);
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public ProcessFileStatus getProcessFileStatus() {
        return processFileStatus;
    }

    public String getFilename() {
        return filename;
    }

    public OffsetDateTime getLastModificationDate() {
        return lastModificationDate;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
