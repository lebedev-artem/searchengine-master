package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

import java.util.List;
@Service
@Transactional
@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {
	/**
	 * Для создания SQL запроса, необходимо указать nativeQuery = true<
	 * каждый параметр в SQL запросе можно вставить, используя запись :ИМЯ_ПЕРЕМEННОЙ
	 * перед именем двоеточие, так hibernate поймет, что надо заменить на значение переменной
	 */
	@Query(value = "SELECT * from words where word LIKE %:wordPart% LIMIT :limit", nativeQuery = true)
	List<SiteEntity> findAllContains(String wordPart, int limit);
}



