package searchengine.services.lemmatization;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.services.indexing.IndexServiceImpl;
import searchengine.services.stuff.StaticVault;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.sleep;

@Slf4j
@Getter
@Setter
@Service
@RequiredArgsConstructor
public class LemmasAndIndexCollectingServiceImpl implements LemmasAndIndexCollectingService {

	private final LemmaRepository lemmaRepository;
	private final IndexRepository indexRepository;
	private final PageRepository pageRepository;
	private final LemmaFinder lemmaFinder;
	private static final Integer INIT_FREQ = 1;
	private volatile boolean savingPagesIsDone;
	private BlockingQueue<Integer> incomeQueue;                                         //idx of pages from saving thread
	private LemmaEntity lemmaEntity;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;
	private IndexEntity indexEntity;
	private Set<IndexEntity> indexEntities = new HashSet<>();
	private Map<String, Integer> collectedLemmas = new HashMap<>();
	private Map<String, LemmaEntity> lemmaBuffer = new HashMap<>();
	private Map<LemmaEntity, IndexEntity> indexBuffer = new HashMap<>();
	private Integer countPages = 0;


	public void startCollecting() {
		long startSiteTime = System.currentTimeMillis();
		while (allowed()) {
			if (pressedStop()) {
				incomeQueue.clear();
				return;
			}

			Integer pageId = incomeQueue.poll();
			if (pageId != null) {
				pageEntity = pageRepository.getReferenceById(pageId);
				Document doc = Jsoup.parse(pageEntity.getContent());
				String text = doc.body().text();
				collectedLemmas = lemmaFinder.collectLemmas(text);

				long startPageTime = System.currentTimeMillis();
				for (String lemma : collectedLemmas.keySet()) {

					int rank = collectedLemmas.get(lemma);
					lemmaEntity = createLemmaEntity(lemma);
//					lemmaBuffer.put(lemmaEntity.getLemma(), lemmaEntity);

					IndexEntity indexEntity = new IndexEntity(new IndexEntity.Id(), pageEntity, lemmaEntity, rank);
					StaticVault.indexEntitiesMap.add(indexEntity);
//					indexEntities.add(indexEntity);
				}

//				countPages++;
//				if (lemmaBuffer.size() > 10000) {
//
//					synchronized (LemmaRepository.class) {
//						lemmaRepository.saveAll(lemmaBuffer.values());
//						indexRepository.saveAll(indexEntities);
//					}
//
//					log.info(logAboutPage(pageId, startPageTime));
//					indexEntities.clear();
//					lemmaBuffer.clear();
//					countPages = 0;
//				}

			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					log.error("Error sleeping while waiting for an item in line");
				}
			}
		}
		lemmaRepository.saveAll(StaticVault.lemmaEntitiesMap.values());
		for (IndexEntity idx: StaticVault.indexEntitiesMap) {

			if (idx.getId().getLemmaId() == null){
				idx.getId().setLemmaId(StaticVault.lemmaEntitiesMap.get(idx.getLemmaEntity().getLemma()).getId());
			}
		}
		indexRepository.saveAll(StaticVault.indexEntitiesMap);
//		indexRepository.saveAll(indexEntities);
		log.warn(logAboutEachSite(startSiteTime));
	}

	public LemmaEntity createLemmaEntity(String lemma) {
		LemmaEntity lemmaObj;
		if (StaticVault.lemmaEntitiesMap.containsKey(lemma)){
			int oldFreq = StaticVault.lemmaEntitiesMap.get(lemma).getFrequency();
			StaticVault.lemmaEntitiesMap.get(lemma).setFrequency(oldFreq + 1);
			lemmaObj = StaticVault.lemmaEntitiesMap.get(lemma);
		}else {
			lemmaObj = new LemmaEntity(siteEntity, lemma, INIT_FREQ);
			StaticVault.lemmaEntitiesMap.put(lemma, lemmaObj);
		}

//		synchronized (LemmaRepository.class) {
//			if (lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity)) {
//				lemmaObj = increaseLemmaFrequency(lemma);
//			} else if (lemmaBuffer.containsKey(lemma)) {
//				lemmaObj = lemmaBuffer.get(lemma);
//			} else {
//				lemmaObj = new LemmaEntity(siteEntity, lemma, INIT_FREQ);
//			}
//		}

		return lemmaObj;
	}

	public LemmaEntity increaseLemmaFrequency(String lemma) {
		LemmaEntity lemmaObj = lemmaRepository.findByLemmaAndSiteEntity(lemma, siteEntity);
		int oldFreq = lemmaObj.getFrequency();
		lemmaObj.setFrequency(oldFreq + 1);
		return lemmaObj;
	}

	private @NotNull String logAboutPage(Integer pageId, long eachPageTime) {
		return lemmaBuffer.size()
				+ " lemmas from " + countPages + " pages"
				+ " collected and saved in " + +(System.currentTimeMillis() - eachPageTime) + " ms"
				+ " incomeQueue has " + incomeQueue.size() + " pages idx";
	}

	private String logAboutEachSite(long startTime) {
		return lemmaRepository.countBySiteEntity(siteEntity)
				+ " lemmas and "
				+ indexRepository.countBySiteId(siteEntity.getId()) + " indexes saved"
				+ " from DB from site url " + siteEntity.getUrl()
				+ " saved in " + (System.currentTimeMillis() - startTime) + " ms";
	}

	public Boolean allowed() {
		return !savingPagesIsDone | incomeQueue.iterator().hasNext();
	}

	public boolean pressedStop() {
		return IndexServiceImpl.pressedStop;
	}
}