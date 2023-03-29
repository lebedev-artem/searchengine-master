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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `lemma` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnLemmaTable();

	boolean existsByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	boolean existsByLemma(String lemma);
	void deleteAllBySiteEntity(SiteEntity siteEntity);
	LemmaEntity findByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	LemmaEntity findFirstByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	Set<LemmaEntity> findAllByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	Integer countBySiteEntity(SiteEntity siteEntity);
	Set<LemmaEntity> findByLemmaIn(Set<String> lemmaEntities);
	LemmaEntity findByLemma(String lemma);
	LemmaEntity findFirstByOrderByFrequencyAsc();
	LemmaEntity findTopByOrderByFrequencyAsc();
	LemmaEntity findTopByOrderByFrequencyDesc();
	LemmaEntity findFirstByLemmaOrderByFrequencyDesc(String lemma);
	LemmaEntity findFirstByLemmaAndSiteEntityOrderByFrequencyAsc(String lemma, SiteEntity siteEntity);

	Integer getFrequencyByLemma(String lemma);
	LemmaEntity getByLemma(String lemma);
	Integer getIdByLemma(String lemma);
	LemmaEntity getByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	LemmaEntity getReferenceByLemma(String lemma);

	@Query(value = "select lemma_id from search_index where page_id = :pageId", nativeQuery = true)
	Set<Integer> findAllLemmaIdByPageId(Integer pageId);
	LemmaEntity getReferenceById(Integer id);
	LemmaEntity findById(Integer id);

}