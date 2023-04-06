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
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

	void deleteById(Integer id);
	boolean existsByUrl(String url);
	SiteEntity findByUrl(String url);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "ALTER TABLE `site` AUTO_INCREMENT = 1", nativeQuery = true)
	void resetIdOnSiteTable();

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status, status_time = :statusTime WHERE `url` = :url",
			nativeQuery = true)
	void updateStatusStatusTimeByUrl(String  status, LocalDateTime statusTime, String url);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status_time` = :statusTime WHERE `name`=:name", nativeQuery = true)
	void updateStatusTime(String name, LocalDateTime statusTime);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `last_error` = :error, status_time = :statusTime WHERE `url` = :url",
			nativeQuery = true)
	void updateErrorStatusTimeByUrl(String error, LocalDateTime statusTime, String url);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status, status_time = :statusTime, `last_error` = :error WHERE `url` = :url",
			nativeQuery = true)
	void updateStatusStatusTimeErrorByUrl(String  status, LocalDateTime statusTime, String error, String url);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE `site` SET `status` = :status, status_time = :statusTime, `last_error` = :error WHERE `status` = \"INDEXING\"",
			nativeQuery = true)
	void updateAllStatusStatusTimeError(String  status, LocalDateTime statusTime, String error);








}



