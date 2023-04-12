package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;
import java.util.Set;

@Transactional
@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {

	Set<IndexEntity> findAllByLemmaEntity(LemmaEntity lemmaEntity);

	List<IndexEntity> findAllByPageEntityIn(List<PageEntity> pageEntities);

	IndexEntity findByLemmaEntityAndPageEntity(LemmaEntity lemmaEntity, PageEntity pageEntity);

}
