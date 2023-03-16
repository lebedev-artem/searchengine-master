package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;
import java.util.Set;

@Transactional
@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `lemma` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnLemmaTable();

	boolean existsByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	void deleteAllBySiteEntity(SiteEntity siteEntity);
	LemmaEntity findByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	LemmaEntity findFirstByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	Set<LemmaEntity> findAllByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	Integer countBySiteEntity(SiteEntity siteEntity);

	Integer getFrequencyByLemma(String lemma);
	LemmaEntity getByLemma(String lemma);
	Integer getIdByLemma(String lemma);
	LemmaEntity getByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);

}