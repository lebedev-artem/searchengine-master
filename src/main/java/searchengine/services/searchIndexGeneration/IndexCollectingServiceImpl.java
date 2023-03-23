package searchengine.services.searchIndexGeneration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.stuff.StaticVault;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Component
@NoArgsConstructor
public class IndexCollectingServiceImpl implements IndexCollectingService {
	private static final Logger rootLogger = LogManager.getRootLogger();
	private boolean lemmasCollectingIsDone = false;
	private volatile boolean indexingStopped = false;
	private BlockingQueue<IndexEntity> queue;
	private BlockingQueue<LemmaEntity> queueOfLemmaEntityToSaveEntities;
	private SiteEntity siteEntity;
	//	private Map<String, LemmaEntity> lemmaEntitiesStaticMap = StaticVault.lemmaEntitiesMap;
//	private Set<SearchIndexEntity> searchIndexEntitiesStaticMap = StaticVault.searchIndexEntitiesMap;
	private Set<IndexEntity> searchIndexEntities = new HashSet<>();
	private LemmaEntity lemmaEntity;
	private IndexEntity searchIndexEntity;
	private boolean saved = false;
	private Map<String, LemmaEntity> lemmaEntitiesStatic = StaticVault.lemmaEntitiesMap;
	private Set<LemmaEntity> lemmaEntitiesOneDrop = new HashSet<>();
	boolean lemmasSaved = false;
	private BlockingQueue<Map.Entry<Map<PageEntity, String>, Integer>> queueIdx;
	private Map.Entry<Map<PageEntity, String>, Integer> idxObj;


	@Autowired
	IndexRepository indexRepository;
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

//			LemmaEntity lemmaE = queueOfLemmaEntityToSaveEntities.poll();
//			if (lemmaE != null) {
//				lemmaEntitiesOneDrop.add(lemmaE);
//			} else {
//				try {
//					sleep(10);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
//			}

//			idxObj = queueIdx.poll();
//			if (idxObj != null){
//				Map<PageEntity, String> i = Objects.requireNonNull(idxObj).getKey();
//				String lemma = i.values().stream().findFirst().get();
//				Integer rank = idxObj.getValue();
//				PageEntity pageEntity = i.keySet().stream().findFirst().get();
//				LemmaEntity lemma1 = lemmaEntitiesStatic.get(lemma);
//				SearchIndexEntity sii = new SearchIndexEntity();
//
//				sii.setLemmaEntity(lemma1);
//				sii.setPageEntity(pageEntity);
//				sii.setLemmaRank(rank);
//				searchIndexEntities.add(sii);
//			} else {
//				try {
//					sleep(10);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
//			}

//			if (lemmasCollectingIsDone) {
//				queue.drainTo(searchIndexEntities);
//			} else
			searchIndexEntity = queue.poll();

			if (searchIndexEntity != null) {
//				synchronized (SearchIndexRepository.class){
//					searchIndexRepository.save(searchIndexEntity);
//				}

				searchIndexEntities.add(searchIndexEntity);

			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (searchIndexEntities.size() >= 100) {
//				lemmaRepository.saveAll(lemmaEntitiesOneDrop);
//				assignLemmaEntityToIndexEntity();
				synchronized (IndexRepository.class) {
					searchIndexEntities.forEach(searchIndexEntity1 -> {
//						if (lemmaRepository.existsByLemmaAndSiteEntity(searchIndexEntity1.getLemmaEntity().getLemma(), siteEntity))
//							System.out.println("asd");
						rootLogger.warn(searchIndexEntity1.getLemmaEntity().getLemma() + "  " + searchIndexEntity1.getLemmaEntity().getFrequency());
						indexRepository.save(searchIndexEntity1);
					});
				}

//				searchIndexRepository.saveAll(searchIndexEntities);

//				searchIndexRepository.saveAll(searchIndexEntities);
				rootLogger.info(lemmaEntitiesOneDrop.size() + " lemmas and " + searchIndexEntities.size() + " idx saved");
				lemmaEntitiesOneDrop.clear();
				searchIndexEntities.clear();
//				searchIndexEntities.clear();
			}
//			else if (searchIndexEntities.size() >= 5_000) {
//				rootLogger.info("5_000 idx and " + lemmaEntitiesOneDrop.size() + " lemmas");
//				lemmaRepository.saveAllAndFlush(lemmaEntitiesOneDrop);
////				assignLemmaEntityToIndexEntity();
//				searchIndexRepository.saveAllAndFlush(searchIndexEntities);
//				rootLogger.info("5_000 idx and " + lemmaEntitiesOneDrop.size() + " lemmas saved. " + queue.size() + " idx in queue");
//				searchIndexEntities.clear();
//				lemmaEntitiesOneDrop.clear();
//				System.gc();
//			}

			if (previousStepDoneAndQueueEmpty() || indexingStopped) {
//				lemmaRepository.saveAll(lemmaEntitiesOneDrop);
//				assignLemmaEntityToIndexEntity();
				indexRepository.saveAll(searchIndexEntities);
				StaticVault.lemmaEntitiesMap.clear();
				queue.clear();
				queueOfLemmaEntityToSaveEntities.clear();
				rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName());
				rootLogger.warn(":: index entries generated and saved in DB, site -> " + siteEntity.getName());
//				rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeLemmasSaving) + " ms");
//				rootLogger.warn(":: " + searchIndexEntitiesStaticMap.size() + " index entries generated and saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeIndexCreatingAndSaving) + " ms");
				return;
			}
		}
	}

	private void assignLemmaEntityToIndexEntity() {
		searchIndexEntities.forEach(idxObj -> {
			if (idxObj.getId().getLemmaId() == null && idxObj.getLemmaEntity().getId() != null)
				idxObj.getId().setLemmaId(idxObj.getLemmaEntity().getId());
		});
	}

	private boolean previousStepDoneAndQueueEmpty() {
		return lemmasCollectingIsDone && !queue.iterator().hasNext();
//				&& !queueOfLemmaEntityToSaveEntities.iterator().hasNext();
	}
}
