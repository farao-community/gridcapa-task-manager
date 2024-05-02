/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vincent BOCHET {@literal <vincent.bochet at rte-france.com>}
 */
@SpringBootTest
class TaskManagerConfigurationPropertiesTest {

    @Autowired
    TaskManagerConfigurationProperties properties;

    @Test
    void getProcessTimezoneTest() {
        ZoneId timezone = properties.getProcessTimezone();
        assertEquals(ZoneId.of("CET"), timezone);
    }
}
