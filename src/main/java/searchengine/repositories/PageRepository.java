package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Transactional
@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {

	void deleteAllBySiteEntity(SiteEntity siteEntity);

	boolean existsByPathAndSiteEntity(String path, SiteEntity siteEntity);

	PageEntity getReferenceById(Integer id);

	Integer countBySiteEntity(SiteEntity siteEntity);

	List<PageEntity> findAllByIdIn(List<Integer> ids);

	List<PageEntity> findAllBySiteEntityAndPathContains(SiteEntity siteEntity, String path);

	List<PageEntity> findBySiteEntityAndPath(SiteEntity siteEntity, String path);

	void deleteById(Integer id);
}
