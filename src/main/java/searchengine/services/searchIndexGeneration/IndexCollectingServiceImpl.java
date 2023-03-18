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
	private Map<String, LemmaEntity> lemmaEntitiesStaticMap = StaticVault.lemmaEntitiesMap;
	private Set<SearchIndexEntity> searchIndexEntitiesStaticMap = StaticVault.searchIndexEntitiesMap;
	private LemmaEntity lemmaEntity;
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
			if (lemmasCollectingIsDone){
				for (SearchIndexEntity s: searchIndexEntitiesStaticMap) {
					s.setLemmaEntity(lemmaEntitiesStaticMap.get(s.getLemmaEntity().getLemma()));
				}

				System.gc();
				System.out.println("updated");
				System.out.println(" lemmas size " + lemmaEntitiesStaticMap.size());
				int count = 0;
				lemmaRepository.saveAll(lemmaEntitiesStaticMap.values());
				System.out.println(lemmaRepository.countBySiteEntity(siteEntity) + " saved");
				StaticVault.lemmaEntitiesMap.clear();
				System.out.println("start saving index");
				int beforasavin = (int) searchIndexRepository.count();
				searchIndexRepository.saveAll(searchIndexEntitiesStaticMap);
				System.out.println( searchIndexRepository.count() - beforasavin + " index entries saved");
				saved = true;
//					lemmaRepository.save(lemmaEntitiesStaticMap.get(s.getLemmaEntity().getLemma()));
			}

			if (notAllowed() || indexingStopped) {
//				searchIndexRepository.saveAll(indexEntitiesSet);
				rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName());
				rootLogger.warn(":: " + searchIndexEntitiesStaticMap.size() + " index entries generated and saved in DB, site -> " + siteEntity.getName());
//				rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeLemmasSaving) + " ms");
//				rootLogger.warn(":: " + searchIndexEntitiesStaticMap.size() + " index entries generated and saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeIndexCreatingAndSaving) + " ms");
				StaticVault.lemmaEntitiesMap.clear();
				StaticVault.searchIndexEntitiesMap.clear();
				return;
			}
		}
	}

	private boolean notAllowed() {
		return lemmasCollectingIsDone && saved;
//		return lemmasCollectingIsDone && !queue.iterator().hasNext();
	}
}
