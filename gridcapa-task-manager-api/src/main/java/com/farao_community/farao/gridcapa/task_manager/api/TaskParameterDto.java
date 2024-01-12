/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Marc Schwitzguebel {@literal <marc.schwitzguebel at rte-france.com>}
 */

public class TaskParameterDto {
    private String id;
    private String parameterType;
    private String value;
    private String defaultValue;

    @JsonCreator
    public TaskParameterDto(
            @JsonProperty("id") String id,
            @JsonProperty("parameterType") String parameterType,
            @JsonProperty("value") String value,
            @JsonProperty("defaultValue") String defaultValue) {
        this.id = id;
        this.parameterType = parameterType;
        this.value = value;
        this.defaultValue = defaultValue;
    }

    public TaskParameterDto(ParameterDto parameterDto) {
        this.id = parameterDto.getId();
        this.parameterType = parameterDto.getParameterType();
        this.value = parameterDto.getValue();
        this.defaultValue = parameterDto.getDefaultValue();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParameterType() {
        return parameterType;
    }

    public void setParameterType(String parameterType) {
        this.parameterType = parameterType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

}
