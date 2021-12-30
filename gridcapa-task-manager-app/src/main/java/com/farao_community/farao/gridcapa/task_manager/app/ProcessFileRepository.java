package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Repository
public interface ProcessFileRepository extends NaturalRepository<ProcessFile, String> {

    @Query("SELECT process_file FROM ProcessFile process_file WHERE " +
        "process_file.startingAvailabilityDate = :startingAvailabilityDate AND process_file.fileType = :fileType")
    Optional<ProcessFile> findProcessFileByStartingAvailabilityDateAndAndFileType(
        @Param("startingAvailabilityDate") OffsetDateTime startingAvailabilityDate,
        @Param("fileType") String fileType);

    @Query("SELECT process_file FROM ProcessFile process_file WHERE process_file.startingAvailabilityDate <= :taskTimestamp AND process_file.endingAvailabilityDate >= :taskTimestamp")
    Set<ProcessFile> findProcessFileByTimestamp(@Param("taskTimestamp") OffsetDateTime taskTimestamp);
}
