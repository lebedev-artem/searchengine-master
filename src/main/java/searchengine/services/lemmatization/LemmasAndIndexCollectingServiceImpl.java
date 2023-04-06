package searchengine.services.lemmatization;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.services.indexing.IndexServiceImpl;

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
	private SiteEntity siteEntity;
	private IndexEntity indexEntity;
	private Set<IndexEntity> indexEntities = new HashSet<>();
	private Map<String, Integer> collectedLemmas = new HashMap<>();
	private Map<String, LemmaEntity> lemmaEntities = new HashMap<>();
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
				PageEntity pageEntity = pageRepository.getReferenceById(pageId);
				collectedLemmas = lemmaFinder.collectLemmas
						(Jsoup.parse(pageEntity
								.getContent()).body().text());

//				long startPageTime = System.currentTimeMillis();
				for (String lemma : collectedLemmas.keySet()) {

					int rank = collectedLemmas.get(lemma);
					LemmaEntity lemmaEntity = createLemmaEntity(lemma);
					indexEntities.add(
							new IndexEntity(
									new IndexEntity.Id(), pageEntity, lemmaEntity, rank));
				}

			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					log.error("Error sleeping while waiting for an item in line");
				}
			}
		}
		long lemmaSave = System.currentTimeMillis();
		lemmaRepository.saveAll(lemmaEntities.values());
		try {
			sleep(200);
		} catch (InterruptedException e) {
			log.error("Error sleeping after saving lemmas");
		}
		log.warn("saving lemmas with " + (System.currentTimeMillis() - lemmaSave) + " ms");
		long idxSave = System.currentTimeMillis();

		indexRepository.saveAll(indexEntities);
		try {
			sleep(200);
		} catch (InterruptedException e) {
			log.error("Error sleeping after saving lemmas");
		}
		log.warn("saving index: " + (System.currentTimeMillis() - idxSave) + " ms");
		log.warn(logAboutEachSite(startSiteTime));
		indexEntities.clear();
		lemmaEntities.clear();
	}

	public LemmaEntity createLemmaEntity(String lemma) {
		LemmaEntity lemmaObj;
		if (lemmaEntities.containsKey(lemma)) {
			int oldFreq = lemmaEntities.get(lemma).getFrequency();
			lemmaEntities.get(lemma).setFrequency(oldFreq + 1);
			lemmaObj = lemmaEntities.get(lemma);
		} else {
			lemmaObj = new LemmaEntity(siteEntity, lemma, INIT_FREQ);
			lemmaEntities.put(lemma, lemmaObj);
		}
		return lemmaObj;
	}


	private @NotNull String logAboutEachSite(long startTime) {
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