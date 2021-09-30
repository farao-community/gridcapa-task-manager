/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager;

import javax.persistence.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Entity
public class Task {

    @Id
    @Column(name = "timestamp")
    private String timestamp;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_file_url_mapping",
            joinColumns = {@JoinColumn(name = "task_timestamp", referencedColumnName = "timestamp")})
    @MapKeyColumn(name = "file_type")
    @Column(name = "file_url", columnDefinition = "TEXT")
    private Map<String, String> fileTypeToUrlMap;

    public Task() {

    }

    public Task(String timestamp) {
        this.timestamp = timestamp;
        this.fileTypeToUrlMap = new HashMap<>();
    }

    public Task(String timestamp, List<String> fileTypes) {
        this.timestamp = timestamp;
        fileTypeToUrlMap = new HashMap<>();
        fileTypes.forEach(inputType -> fileTypeToUrlMap.put(inputType, null));
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getFileTypeToUrlMap() {
        return fileTypeToUrlMap;
    }

    public void setFileTypeToUrlMap(Map<String, String> fileTypeToUrlMap) {
        this.fileTypeToUrlMap = fileTypeToUrlMap;
    }
}
