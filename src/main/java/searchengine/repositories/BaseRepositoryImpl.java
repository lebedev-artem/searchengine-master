package searchengine.repositories;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.BaseEntity;
import javax.persistence.EntityManager;
import java.io.Serializable;

public class BaseRepositoryImpl<T extends BaseEntity, ID extends Serializable> extends SimpleJpaRepository<T, ID> implements BaseRepository<T, ID> {

	private final EntityManager em;

	public BaseRepositoryImpl (JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
		super(entityInformation, entityManager);
		this.em = entityManager;
	}

	@Transactional
	@Override
	public void delete(BaseEntity entity) {
		entity.setDeleted(true);
		em.persist(entity);
	}

	@Override
	public void resetIndex() {
	}
}
