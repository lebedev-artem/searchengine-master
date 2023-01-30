package searchengine.repositories;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

@Transactional
@Repository
public interface PageEntityRepository extends BaseRepository<PageEntity, Long> {
}
