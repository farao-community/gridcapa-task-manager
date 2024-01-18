/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskParameterDtoTest {

    @Test
    void buildTest() {
        String identifiant = "identifiant";
        String aBoolean = "BOOLEAN";
        String aTrue = "true";
        String aFalse = "false";
        TaskParameterDto dto = new TaskParameterDto(identifiant, aBoolean, aTrue, aFalse);
        Assertions.assertEquals(identifiant, dto.getId());
        Assertions.assertEquals(aBoolean, dto.getParameterType());
        Assertions.assertEquals(aTrue, dto.getValue());
        Assertions.assertEquals(aFalse, dto.getDefaultValue());
    }

    @Test
    void buildFromParameterTest() {
        String identifiant = "identifiant2";
        String aBoolean = "BOOLEAN_";
        String aTrue = "TRUE";
        String aFalse = "FALSE";
        TaskParameterDto dto = new TaskParameterDto(new ParameterDto(identifiant, "name", 12,  aBoolean, "title of section", 1, aTrue, aFalse));
        Assertions.assertEquals(identifiant, dto.getId());
        Assertions.assertEquals(aBoolean, dto.getParameterType());
        Assertions.assertEquals(aTrue, dto.getValue());
        Assertions.assertEquals(aFalse, dto.getDefaultValue());
    }

}
