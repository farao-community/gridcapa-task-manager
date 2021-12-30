package com.farao_community.farao.gridcapa.task_manager.app;

import org.hibernate.Session;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.Serializable;
import java.util.Optional;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Transactional(readOnly = true)
public class NaturalRepositoryImpl<T, I extends Serializable> extends SimpleJpaRepository<T, I> implements NaturalRepository<T, I> {

    private final EntityManager entityManager;

    public NaturalRepositoryImpl(JpaEntityInformation<T, I> entityInformation,
                                 EntityManager entityManager) {
        super(entityInformation, entityManager);

        this.entityManager = entityManager;
    }

    @Override
    public Optional<T> findBySimpleNaturalId(I naturalId) {
        return entityManager.unwrap(Session.class)
            .bySimpleNaturalId(this.getDomainClass())
            .loadOptional(naturalId);
    }
}
