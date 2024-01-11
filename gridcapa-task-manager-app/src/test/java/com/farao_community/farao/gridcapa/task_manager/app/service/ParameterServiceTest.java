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
import java.util.Optional;

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

    @Test
    void setParameterValueOkTest() {
        Long id = 1515L;
        String value = "Amazing value";
        Parameter initialParameter = new Parameter();
        initialParameter.setId(id);
        initialParameter.setParameterType(Parameter.ParameterType.STRING);
        initialParameter.setValue("Poor and annoying value");
        Parameter newParameter = new Parameter();
        newParameter.setId(id);
        newParameter.setParameterType(Parameter.ParameterType.STRING);
        newParameter.setValue(value);
        Mockito.when(parameterRepository.findById(id)).thenReturn(Optional.of(initialParameter));
        Mockito.when(parameterRepository.save(Mockito.any())).thenReturn(newParameter);

        ParameterDto parameterDto = parameterService.setParameterValue(id, value);

        Assertions.assertThat(parameterDto)
            .isNotNull()
            .hasFieldOrPropertyWithValue("id", id)
            .hasFieldOrPropertyWithValue("value", value);
    }

    @Test
    void setParameterValueNotFoundTest() {
        Long id = 1515L;
        String value = "Amazing value";
        Mockito.when(parameterRepository.findById(id)).thenReturn(Optional.empty());

        ParameterDto parameterDto = parameterService.setParameterValue(id, value);

        Assertions.assertThat(parameterDto).isNull();
    }
}
