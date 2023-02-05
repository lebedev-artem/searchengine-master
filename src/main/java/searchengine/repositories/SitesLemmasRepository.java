package searchengine.repositories;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SitesLemmasEntity;

@Transactional
@Repository
public interface SitesLemmasRepository extends BaseRepository<SitesLemmasEntity, Long>{
}
