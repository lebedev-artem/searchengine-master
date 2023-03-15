package searchengine.services.searchIndexGeneration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.LemmaEntity;
import searchengine.model.SearchIndexEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.stuff.StaticVault;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Component
@NoArgsConstructor
public class IndexCollectingServiceImpl {
	private static final Logger rootLogger = LogManager.getRootLogger();
	private boolean lemmasCollectingIsDone = false;
	private volatile boolean indexingStopped = false;
	private BlockingQueue<SearchIndexEntity> queue;
	private SiteEntity siteEntity;
	private Map<String, LemmaEntity> lemmaEntitiesMap = StaticVault.lemmaEntityMap;
	private LemmaEntity lemmaEntity;

	@Autowired
	SearchIndexRepository searchIndexRepository;
	@Autowired
	SiteRepository siteRepository;
	@Autowired
	PageRepository pageRepository;
	@Autowired
	LemmaRepository lemmaRepository;

	public void indexCollect() {
		lemmasCollectingIsDone = false;
		long timeIndexCreatingAndSaving = System.currentTimeMillis();
		Set<SearchIndexEntity> indexEntitiesSet = new HashSet<>();
		while (true) {
			SearchIndexEntity searchIndexEntity = queue.poll();
			if (searchIndexEntity != null) {
				lemmaEntity = lemmaRepository.getByLemma(searchIndexEntity.getLemmaEntity().getLemma());
				searchIndexEntity.setLemmaEntity(lemmaEntity);
				indexEntitiesSet.add(searchIndexEntity);
			} else {
				try {
					sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (notAllowed() || indexingStopped) {
//				for (SearchIndexEntity sIE: indexEntitiesSet) {
//					System.out.println("page_id - " + sIE.getPageEntity().getId() + " lemma_id - " + sIE.getLemmaEntity().getId() + " lemma - " + sIE.getLemmaEntity().getLemma() + " rank - " + sIE.getLemmaRank());
//					searchIndexRepository.save(sIE);
//				}

				searchIndexRepository.saveAll(indexEntitiesSet);
				rootLogger.warn(":: " + indexEntitiesSet.size() + " index entries generated and saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeIndexCreatingAndSaving) + " ms");
				lemmaEntitiesMap.clear();
				return;
			}
		}
	}

	private boolean notAllowed() {
		return lemmasCollectingIsDone && !queue.iterator().hasNext();
	}
}
