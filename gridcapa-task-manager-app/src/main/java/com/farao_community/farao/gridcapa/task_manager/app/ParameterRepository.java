/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Parameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Marc Schwitzguebel {@literal <marc.schwitzguebel at rte-france.com>}
 */

@Repository
public interface ParameterRepository extends JpaRepository<Parameter, String> {

}
