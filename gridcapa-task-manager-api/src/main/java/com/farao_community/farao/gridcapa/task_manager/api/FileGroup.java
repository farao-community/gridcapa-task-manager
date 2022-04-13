/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public enum FileGroup {
    ARTIFACT("Artifact"),
    INPUT("Input"),
    OUTPUT("Output"),
    EXTENDED_OUTPUT("Extended-output"),
    NONE("None");

    private final String metadataValue;

    FileGroup(String metadataValue) {
        this.metadataValue = metadataValue;
    }

    public String getMetadataValue() {
        return metadataValue;
    }
}
