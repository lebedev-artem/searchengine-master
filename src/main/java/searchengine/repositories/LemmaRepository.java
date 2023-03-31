package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Set;

@Transactional
@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

	void deleteAllBySiteEntity(SiteEntity siteEntity);
	boolean existsByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);

	LemmaEntity findById(Integer id);
	Integer countBySiteEntity(SiteEntity siteEntity);
	LemmaEntity getByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	LemmaEntity findByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `lemma` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnLemmaTable();

	@Query(value = "select lemma_id from search_index where page_id = :pageId", nativeQuery = true)
	Set<Integer> findAllLemmaIdByPageId(Integer pageId);

}