package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

import java.time.LocalDateTime;

@Transactional
@Repository
public interface SiteEntityRepository extends BaseRepository<SiteEntity, Long> {

	/**
	 * Для создания SQL запроса, необходимо указать nativeQuery = true<
	 * каждый параметр в SQL запросе можно вставить, используя запись :ИМЯ_ПЕРЕМEННОЙ
	 * перед именем двоеточие, так hibernate поймет, что надо заменить на значение переменной
	 */

//	@Query(value = "SELECT * from site where name LIKE %:namePart% LIMIT :limit", nativeQuery = true)
//	List<SiteEntity> findAllContains(String namePart, int limit);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE from `site` WHERE `name` = :name", nativeQuery = true)
	void deleteByName(String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status WHERE `name`=:name", nativeQuery = true)
	void updateSiteStatus(String status, String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "INSERT INTO `site` (status, status_time, last_error, url, name) VALUES (:status, :statusTime, :lastError, :url, :name)", nativeQuery = true)
	void saveSiteEntity(String status, LocalDateTime statusTime, String lastError, String url, String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status_time` = :statusTime WHERE `name`=:name", nativeQuery = true)
	void updateStatusTime(String name, LocalDateTime statusTime);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status, status_time = :statusTime, `last_error` = :error WHERE `status` = \"INDEXING\"", nativeQuery = true)
	void updateAllSitesStatusTimeError(String status, LocalDateTime statusTime, String error);
}



