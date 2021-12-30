package com.farao_community.farao.gridcapa.task_manager.app;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.Optional;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@NoRepositoryBean
public interface NaturalRepository<T, I extends Serializable> extends JpaRepository<T, I> {

    Optional<T> findBySimpleNaturalId(I naturalId);
}
