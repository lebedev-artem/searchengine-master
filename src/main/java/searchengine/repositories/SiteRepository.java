package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

@Transactional
@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

	void deleteById(Integer id);

	boolean existsByUrl(String url);

	SiteEntity findByUrl(String url);

	SiteEntity findById(Integer id);


}



