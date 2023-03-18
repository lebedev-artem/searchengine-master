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
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Component
@NoArgsConstructor
public class IndexCollectingServiceImpl implements IndexCollectingService{
	private static final Logger rootLogger = LogManager.getRootLogger();
	private boolean lemmasCollectingIsDone = false;
	private volatile boolean indexingStopped = false;
	private BlockingQueue<SearchIndexEntity> queue;
	private SiteEntity siteEntity;
	//	private Map<String, LemmaEntity> lemmaEntitiesStaticMap = StaticVault.lemmaEntitiesMap;
//	private Set<SearchIndexEntity> searchIndexEntitiesStaticMap = StaticVault.searchIndexEntitiesMap;
	private Set<SearchIndexEntity> searchIndexEntities = new HashSet<>();
	private LemmaEntity lemmaEntity;
	private SearchIndexEntity searchIndexEntity;
	private boolean saved = false;

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

		while (true) {
			searchIndexEntity = queue.poll();
			if (searchIndexEntity != null) {
				searchIndexEntities.add(searchIndexEntity);
			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (searchIndexEntities.size() == 10_000) {
				searchIndexRepository.saveAll(searchIndexEntities);
				searchIndexEntities.clear();
			}

			if (previousStepDoneAndQueueEmpty() || indexingStopped) {
				searchIndexRepository.saveAll(searchIndexEntities);
				rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName());
				rootLogger.warn(":: index entries generated and saved in DB, site -> " + siteEntity.getName());
//				rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeLemmasSaving) + " ms");
//				rootLogger.warn(":: " + searchIndexEntitiesStaticMap.size() + " index entries generated and saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeIndexCreatingAndSaving) + " ms");
				return;
			}
		}
	}

	private boolean previousStepDoneAndQueueEmpty() {
		return lemmasCollectingIsDone && !queue.iterator().hasNext();
	}
}
