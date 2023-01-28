package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

import java.time.LocalDateTime;

@Service
@Transactional
@org.springframework.stereotype.Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

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
	void changeSiteStatus(String status, String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "INSERT INTO `site` (status, status_time, last_error, url, name) VALUES (:status, :statusTime, :lastError, :url, :name)", nativeQuery = true)
	void saveSiteEntity(String status, LocalDateTime statusTime, String lastError, String url, String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE from `site`;" + "ALTER TABLE `site` AUTO_INCREMENT = 0", nativeQuery = true)
	void deleteAllAndResetIndex();

}



