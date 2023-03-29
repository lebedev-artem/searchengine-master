//package searchengine.bucket;
//import org.springframework.data.jpa.repository.support.JpaEntityInformation;
//import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
//
//import javax.persistence.EntityManager;
//import java.io.Serializable;
//
//public class BaseRepositoryImpl<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> implements BaseRepository<T, ID> {
//
//	private final EntityManager entityManager;
//
//	public BaseRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
//		super(entityInformation, entityManager);
//		this.entityManager = entityManager;
//	}
//
//	@Override
//	public void resetIdOnSiteTable() {
//	}
//
//	@Override
//	public void resetIdOnPageTable() {
//	}
//
//	@Override
//	public void resetIdOnLemmaTable() {
//	}
//
//	@Override
//	public void resetIdOnIndexTable() {
//	}
//
//}
