package searchengine.repositories;
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
public interface SiteRepository extends BaseRepository<SiteEntity, Long> {

	/**
	 * Для создания SQL запроса, необходимо указать nativeQuery = true<
	 * каждый параметр в SQL запросе можно вставить, используя запись :ИМЯ_ПЕРЕМEННОЙ
	 * перед именем двоеточие, так hibernate поймет, что надо заменить на значение переменной
	 */

//	@Query(value = "SELECT * from site where name LIKE %:namePart% LIMIT :limit", nativeQuery = true)
//	List<SiteEntity> findAllContains(String namePart, int limit);

/*
Надо проверить скорость работы с параметром и без clearAutomatically = true, flushAutomatically = true
 */

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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM SiteEntity s WHERE s.url = :url")
	void removeAllByUrl(@Param("url") String url);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE from `site` WHERE `name` = :name", nativeQuery = true)
	void deleteByName(String name);

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



