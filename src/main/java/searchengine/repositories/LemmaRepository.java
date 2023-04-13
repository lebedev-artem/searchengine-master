package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

@Transactional
@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {

	void deleteAllInBatchBySiteEntity(SiteEntity siteEntity);

	Integer countBySiteEntity(SiteEntity siteEntity);

	LemmaEntity getByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);

	LemmaEntity findByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);
}