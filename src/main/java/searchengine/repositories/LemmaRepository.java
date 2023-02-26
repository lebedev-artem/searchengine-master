package searchengine.repositories;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

@Transactional
@Repository
public interface LemmaRepository extends BaseRepository<LemmaEntity, Long>{
	boolean existsByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	LemmaEntity findByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
	Integer getFrequencyByLemma(String lemma);
}