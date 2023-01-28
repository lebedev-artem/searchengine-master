package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

@Service
@Transactional
@org.springframework.stereotype.Repository
public interface PageRepository extends JpaRepository<PageEntity, Long> {
}
