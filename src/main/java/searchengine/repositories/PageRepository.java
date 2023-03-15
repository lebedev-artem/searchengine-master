package searchengine.repositories;

import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Set;

@Transactional
@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `page` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnPageTable();

	//	@Query("SELECT s FROM SiteEntity s WHERE s.url = :url")
	PageEntity findByPath(String path);
	List<PageEntity> findAllByPath(String path);
	void deleteAllByPath(String path);
	boolean existsByPath(String path);
	boolean existsBySiteEntity(SiteEntity siteEntity);
	boolean existsByPathAndSiteEntity(String path, SiteEntity siteEntity);
	boolean existsById(Integer id);
	PageEntity findByPathAndSiteEntity(String path, SiteEntity siteEntity);
	Set<PageEntity> findAllBySiteEntity(SiteEntity siteEntity);
	Integer countBySiteEntity(SiteEntity siteEntity);
	void deleteAllBySiteEntity(SiteEntity siteEntity);


	@Override
	<S extends PageEntity> boolean exists(Example<S> example);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE FROM `page` WHERE `path` LIKE %:path% and site_id = :siteId", nativeQuery = true)
	void deletePagesBySiteIdContainingPath(String path, Integer siteId);

	@Query(value = "SELECT * FROM `page` WHERE `site_id` = :siteId", nativeQuery = true)
	List<PageEntity> findAllBySiteId(Integer siteId);

	@Query(value = "SELECT count(*) FROM `page` WHERE `site_id` = :siteId", nativeQuery = true)
	Integer countBySiteId(Integer siteId);

	@Query(value = "SELECT count(*) FROM `page`", nativeQuery = true)
	Integer countAllPages();


}
