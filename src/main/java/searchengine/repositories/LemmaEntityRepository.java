package searchengine.repositories;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;

@Transactional
@Repository
public interface LemmaEntityRepository extends BaseRepository<LemmaEntity, Long>{
}
