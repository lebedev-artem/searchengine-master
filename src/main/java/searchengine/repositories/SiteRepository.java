package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.model.StatusIndexing;

import java.time.LocalDateTime;
import java.util.List;

@Transactional
@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `site` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnSiteTable();

	@Query("SELECT COUNT(*) FROM SiteEntity")
	int findCount();

	@Query("SELECT s FROM SiteEntity s WHERE s.status = :status")
	Iterable<SiteEntity> findByStatus(@Param("status") StatusIndexing statusIndexing);

	@Query("SELECT s FROM SiteEntity s")
	List<SiteEntity> findAllSites();

//	@Query("SELECT s FROM SiteEntity s WHERE s.url = :url")

	Integer findIdByName(String name);
	SiteEntity findByName(String name);
	SiteEntity findById(Integer id);
	SiteEntity findByUrl(String url);
	boolean existsByName(String name);
	void deleteByUrl(String url);
//	void deleteById(Integer id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM SiteEntity s WHERE s.url = :url")
	void removeAllByUrl(@Param("url") String url);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE from `site` WHERE `name` = :name", nativeQuery = true)
	void deleteByName(String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE from `site` WHERE `id` = :id", nativeQuery = true)
	void deleteById(Integer id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status WHERE `name`=:name", nativeQuery = true)
	void updateStatus(String status, String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "INSERT INTO `site` (status, status_time, last_error, url, name) VALUES (:status, :statusTime, :lastError, :url, :name)", nativeQuery = true)
	void saveSiteEntity(String status, LocalDateTime statusTime, String lastError, String url, String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status_time` = :statusTime WHERE `name`=:name", nativeQuery = true)
	void updateStatusTime(String name, LocalDateTime statusTime);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status, status_time = :statusTime, `last_error` = :error WHERE `status` = \"INDEXING\"", nativeQuery = true)
	void updateAllStatusStatusTimeError(String status, LocalDateTime statusTime, String error);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status, status_time = :statusTime, `last_error` = :error WHERE `name` = :name", nativeQuery = true)
	void updateStatusStatusTimeError(String status, LocalDateTime statusTime, String error, String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status, status_time = :statusTime WHERE `name` = :name", nativeQuery = true)
	void updateStatusStatusTime(String status, LocalDateTime statusTime, String name);



}



