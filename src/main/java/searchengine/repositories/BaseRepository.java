package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import searchengine.model.BaseEntity;

import java.io.Serializable;

@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID extends Serializable> extends JpaRepository<T, ID> {

//		void delete(T entity);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `site` AUTO_INCREMENT = 0", nativeQuery = true)
	void resetIdOnSiteTable();
}
