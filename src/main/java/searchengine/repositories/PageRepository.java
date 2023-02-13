package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

import java.util.List;

@Transactional
@Repository
public interface PageRepository extends BaseRepository<PageEntity, Long> {

	//	@Query("SELECT s FROM SiteEntity s WHERE s.url = :url")
	PageEntity findByPath(String path);
	List<PageEntity> findAllByPath(String path);
	void deleteAllByPath(String path);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE FROM `page` WHERE `path` LIKE %:path%", nativeQuery = true)
	void deletePagesContainingPath(String path);

	@Query(value = "SELECT * FROM `page` WHERE `site_id` = :siteId", nativeQuery = true)
	List<PageEntity> findAllBySiteId(Integer siteId);

}
