package searchengine.repositories;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import javax.persistence.criteria.CriteriaBuilder;
import java.util.List;
import java.util.Set;

@Transactional
@Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {

	void deleteById(Integer id);
	void deleteAllBySiteEntity(SiteEntity siteEntity);
	boolean existsByPathAndSiteEntity(String path, SiteEntity siteEntity);

	Integer countBySiteEntity(SiteEntity siteEntity);
	PageEntity findByPath(String path);
	PageEntity getReferenceById(Integer id);
	List<PageEntity> findAllByIdIn(List<Integer> ids);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `page` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnPageTable();

	@Query(value = "SELECT `path` FROM `page` WHERE `path` LIKE %:path% and site_id = :siteId", nativeQuery = true)
	Set<String> findPagesBySiteIdContainingPath(String path, Integer siteId);


}
