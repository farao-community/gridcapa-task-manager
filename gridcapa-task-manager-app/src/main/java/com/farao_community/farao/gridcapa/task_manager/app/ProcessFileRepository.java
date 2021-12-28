package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public interface ProcessFileRepository extends JpaRepository<ProcessFile, UUID> {

    Optional<ProcessFile> findByFileObjectKey(String fileObjectKey);
}
