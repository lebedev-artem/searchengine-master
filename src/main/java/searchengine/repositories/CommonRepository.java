package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.CommonEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;

@Service
@Transactional
@Repository
public interface CommonRepository extends JpaRepository<CommonEntity, String> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `site` AUTO_INCREMENT = 0", nativeQuery = true)
	void resetIndex();
}
