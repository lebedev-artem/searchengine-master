package searchengine.repositories;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

@Transactional
@Repository
public interface PageRepository extends BaseRepository<PageEntity, Long> {
}
