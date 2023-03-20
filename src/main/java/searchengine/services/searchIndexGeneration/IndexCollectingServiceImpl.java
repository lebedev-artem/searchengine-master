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

import java.util.*;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Component
@NoArgsConstructor
public class IndexCollectingServiceImpl implements IndexCollectingService {
	private static final Logger rootLogger = LogManager.getRootLogger();
	private boolean lemmasCollectingIsDone = false;
	private volatile boolean indexingStopped = false;
	private BlockingQueue<SearchIndexEntity> queue;
	private BlockingQueue<LemmaEntity> queueOfLemmaEntityToSaveEntities;
	private SiteEntity siteEntity;
	//	private Map<String, LemmaEntity> lemmaEntitiesStaticMap = StaticVault.lemmaEntitiesMap;
//	private Set<SearchIndexEntity> searchIndexEntitiesStaticMap = StaticVault.searchIndexEntitiesMap;
	private Set<SearchIndexEntity> searchIndexEntities = new HashSet<>();
	private LemmaEntity lemmaEntity;
	private SearchIndexEntity searchIndexEntity;
	private boolean saved = false;
	private Map<String, LemmaEntity> lemmaEntitiesStatic = StaticVault.lemmaEntitiesMap;
	private Set<LemmaEntity> lemmaEntitiesOneDrop = new HashSet<>();
	boolean lemmasSaved = false;


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

			LemmaEntity lemmaE = queueOfLemmaEntityToSaveEntities.poll();
			if (lemmaE != null) {
				lemmaEntitiesOneDrop.add(lemmaE);
			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

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

			if (lemmaEntitiesOneDrop.size() == 500 ){
				lemmaRepository.saveAll(lemmaEntitiesOneDrop);
				assignLemmaEntityToIndexEntity();
				searchIndexEntities.clear();
			}

			if (searchIndexEntities.size() == 10_000) {
				lemmaRepository.saveAll(lemmaEntitiesOneDrop);
				assignLemmaEntityToIndexEntity();
//				searchIndexEntities.forEach(searchIndexEntity1 -> {
//					if (searchIndexEntity1.getLemmaEntity().getId() == null){
//						System.out.println("null lemma id");
//						System.out.println(searchIndexEntity1.getId() + " " + searchIndexEntity1.getLemmaEntity() + " " + searchIndexEntity1.getPageEntity());
//						searchIndexEntities.remove(searchIndexEntity1);
//					}
//				});
				searchIndexRepository.saveAll(searchIndexEntities);
				searchIndexEntities.clear();
				lemmaEntitiesOneDrop.clear();
				System.gc();
			}
//
//			System.out.println("index queue size " + queue.size());
//			System.out.println("lemmas saver queue size " + queueOfLemmaEntityToSaveEntities.size());

//		if (searchIndexEntities.size() == 10_000) {
//				searchIndexRepository.saveAll(searchIndexEntities);
//				searchIndexEntities.clear();
//			}

			if (previousStepDoneAndQueueEmpty() || indexingStopped) {
				queue.clear();
				queueOfLemmaEntityToSaveEntities.clear();
				lemmaRepository.saveAll(lemmaEntitiesOneDrop);
				searchIndexRepository.saveAll(searchIndexEntities);
				StaticVault.lemmaEntitiesMap.clear();
				rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName());
				rootLogger.warn(":: index entries generated and saved in DB, site -> " + siteEntity.getName());
//				rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeLemmasSaving) + " ms");
//				rootLogger.warn(":: " + searchIndexEntitiesStaticMap.size() + " index entries generated and saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeIndexCreatingAndSaving) + " ms");
				return;
			}
		}
	}

	private void assignLemmaEntityToIndexEntity() {
		searchIndexEntities.forEach(s -> {
			if (s.getLemmaEntity().getId() == null) {
				LemmaEntity lE = lemmaEntitiesStatic.get(s.getLemmaEntity().getLemma());
//						if (lE == null){
//							System.out.println("null");
//						}
				s.setLemmaEntity(lE);
//						s.getId().setLemmaId(lE.getId());
			}
		});
	}

	private boolean previousStepDoneAndQueueEmpty() {
		return lemmasCollectingIsDone && !queue.iterator().hasNext();
	}
}
