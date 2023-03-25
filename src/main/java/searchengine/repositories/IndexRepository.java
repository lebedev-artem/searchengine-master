package searchengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Transactional
@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `search_index` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnIndexTable();


	@Query(
			value ="SELECT COUNT(`lemma_id`) FROM `search_index` AS s JOIN `lemma` AS l ON l.id = s.lemma_id WHERE l.site_id = :siteId group by site_id",
			nativeQuery = true)
	Integer countBySiteId(Integer siteId);

}
