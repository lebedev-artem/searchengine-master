package searchengine.repositories;

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

}
