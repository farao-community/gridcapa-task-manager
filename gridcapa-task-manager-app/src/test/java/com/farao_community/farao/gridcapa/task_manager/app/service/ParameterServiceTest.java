/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ParameterDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
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
        parameter.setId("test1");
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
                .hasFieldOrPropertyWithValue("id", "test1")
                .hasFieldOrPropertyWithValue("name", "Test name")
                .hasFieldOrPropertyWithValue("displayOrder", 5)
                .hasFieldOrPropertyWithValue("parameterType", "BOOLEAN")
                .hasFieldOrPropertyWithValue("sectionTitle", "Best parameters section")
                .hasFieldOrPropertyWithValue("value", "The value")
                .hasFieldOrPropertyWithValue("defaultValue", "true");

    }

    @Test
    void getParametersConfigurationThrowsExTest() {
        Parameter parameter = new Parameter();
        parameter.setId("unknown");
        List<Parameter> parameterList = List.of(parameter);
        Mockito.when(parameterRepository.findAll()).thenReturn(parameterList);

        Assertions.assertThatExceptionOfType(TaskManagerException.class)
                .isThrownBy(() -> parameterService.getParameters());

    }

    @Test
    void setParameterValueOkTest() {
        String id = "test3";
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
        Mockito.when(parameterRepository.saveAll(Mockito.anyList())).thenReturn(List.of(newParameter));

        ParameterDto newParameterDto = new ParameterDto(newParameter.getId(), newParameter.getName(), newParameter.getDisplayOrder(), newParameter.getParameterType().name(), newParameter.getSectionTitle(), newParameter.getSectionOrder(), newParameter.getValue(), "");
        List<ParameterDto> parameterDto = parameterService.setParameterValues(List.of(newParameterDto));

        Assertions.assertThat(parameterDto)
            .isNotNull()
            .isNotEmpty()
            .element(0)
            .hasFieldOrPropertyWithValue("id", id)
            .hasFieldOrPropertyWithValue("value", value);
    }

    @Test
    void setParameterValueNotFoundTest() {
        String id = "1515L";
        String value = "Amazing value";
        Mockito.when(parameterRepository.findById(id)).thenReturn(Optional.empty());

        List<ParameterDto> parameterDtos = List.of(new ParameterDto("unknown", null, 1, null, null, 2, null, null));
        List<ParameterDto> parameterDto = parameterService.setParameterValues(parameterDtos);

        Assertions.assertThat(parameterDto)
            .isNotNull()
            .isEmpty();
    }
}
