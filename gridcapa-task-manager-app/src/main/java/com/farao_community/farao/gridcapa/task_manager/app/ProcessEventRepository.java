package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @author arnouldjpi
 */

@Repository
public interface ProcessEventRepository extends JpaRepository<ProcessEvent, UUID> {

    void deleteByTimestampBefore(OffsetDateTime timestamp);

}
