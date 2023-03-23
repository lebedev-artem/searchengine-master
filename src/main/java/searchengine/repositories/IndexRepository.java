package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;

@Transactional
@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `search_index` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnIndexTable();


}
