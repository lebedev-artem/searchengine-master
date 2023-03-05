package searchengine.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Set;

@Transactional
@Repository
public interface LemmaRepository extends BaseRepository<LemmaEntity, Long>{
	boolean existsByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	LemmaEntity findByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	LemmaEntity findFirstByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	Set<LemmaEntity> findAllByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);

	Integer getFrequencyByLemma(String lemma);
	LemmaEntity getByLemma(String lemma);
	Integer getIdByLemma(String lemma);

	@Query(value = "SELECT count(*) FROM `lemma`", nativeQuery = true)
	Integer countAllLemmas();
}