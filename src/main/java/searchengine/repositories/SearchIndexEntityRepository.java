package searchengine.repositories;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SearchIndexEntity;

@Transactional
@Repository
public interface SearchIndexEntityRepository extends BaseRepository<SearchIndexEntity, Long> {
}
