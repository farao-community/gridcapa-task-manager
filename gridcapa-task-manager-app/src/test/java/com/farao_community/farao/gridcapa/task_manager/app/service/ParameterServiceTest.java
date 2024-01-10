/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ParameterDto;
import com.farao_community.farao.gridcapa.task_manager.app.ParameterRepository;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Parameter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Marc Schwitzguebel {@literal <marc.schwitzguebel at rte-france.com>}
 */

@SpringBootTest
class ParameterServiceTest {
    @MockBean
    private ParameterRepository parameterRepository;

    @Autowired
    private ParameterService parameterService;

    @Test
    void getParametersConfigurationTest() {
        Parameter parameter = new Parameter();
        parameter.setId(12L);
        parameter.setName("Test name");
        parameter.setDisplayOrder(5);
        parameter.setParameterType(Parameter.ParameterType.BOOLEAN);
        parameter.setSectionTitle("Best parameters section");
        parameter.setValue("The value");
        List<Parameter> parameterList = List.of(parameter);
        Mockito.when(parameterRepository.findAll()).thenReturn(parameterList);

        List<ParameterDto> dtoList = parameterService.getParameters();

        Assertions.assertThat(dtoList)
            .hasSize(1)
            .element(0)
                .hasFieldOrPropertyWithValue("id", 12L)
                .hasFieldOrPropertyWithValue("name", "Test name")
                .hasFieldOrPropertyWithValue("displayOrder", 5)
                .hasFieldOrPropertyWithValue("parameterType", "BOOLEAN")
                .hasFieldOrPropertyWithValue("sectionTitle", "Best parameters section")
                .hasFieldOrPropertyWithValue("value", "The value");
    }
}
