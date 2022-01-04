/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mohamed Benrejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class TaskLogEventUpdateTest {

    @Test
    void testConstructorLogEventUpdate() {
        TaskLogEventUpdate taskLogEventUpdate =  new TaskLogEventUpdate("fake-id", "fake-ts", "fake-level", "fake-message", "fake-service-name");
        assertEquals("fake-id", taskLogEventUpdate.getId());
        assertEquals("fake-ts", taskLogEventUpdate.getTimestamp());
        assertEquals("fake-level", taskLogEventUpdate.getLevel());
        assertEquals("fake-message", taskLogEventUpdate.getMessage());
        assertEquals("fake-service-name", taskLogEventUpdate.getServiceName());
    }
}
