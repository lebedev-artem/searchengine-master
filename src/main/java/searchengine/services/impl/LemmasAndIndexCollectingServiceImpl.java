package searchengine.services.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.services.LemmasAndIndexCollectingService;
import searchengine.tools.LemmaFinder;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import static java.lang.Thread.sleep;

@Slf4j
@Getter
@Setter
@Service
@RequiredArgsConstructor
public class LemmasAndIndexCollectingServiceImpl implements LemmasAndIndexCollectingService {

	private Boolean enabled = true;
	private Integer countPages = 0;
	private Integer countLemmas = 0;
	private Integer countIndexes = 0;
	private SiteEntity siteEntity;
	private IndexEntity indexEntity;
	private final LemmaFinder lemmaFinder;
	private static final Integer INIT_FREQ = 1;
	private boolean savingPagesIsDone;
	private BlockingQueue<Integer> incomeQueue;
	private Set<IndexEntity> indexEntities = new HashSet<>();
	private Map<String, Integer> collectedLemmas = new HashMap<>();
	private Map<String, LemmaEntity> lemmaEntities = new HashMap<>();
	private final PageRepository pageRepository;
	private final IndexRepository indexRepository;
	private final LemmaRepository lemmaRepository;

	public void startCollecting() {
		while (allowed()) {

			if (!enabled) {
				actionsAfterStop();
				return;
			}

			Integer pageId = incomeQueue.poll();
			if (pageId != null) {
				PageEntity pageEntity = pageRepository.getReferenceById(pageId);
				collectedLemmas = lemmaFinder.collectLemmas
						(Jsoup.clean(pageEntity.getContent(), Safelist.simpleText()));

				for (String lemma : collectedLemmas.keySet()) {
					int rank = collectedLemmas.get(lemma);
					LemmaEntity lemmaEntity = createLemmaEntity(lemma);
					indexEntities.add(new IndexEntity(pageEntity, lemmaEntity, rank));
					countIndexes++;
				}
			} else {
				sleeping(10, "Error sleeping while waiting for an item in line");
			}
		}
		savingLemmas();
		savingIndexes();
		log.warn(logAboutEachSite());
	}

	@Override
	public void setEnabled(boolean value) {
		enabled = value;
	}

	private static void sleeping(int millis, String s) {
		try {
			sleep(millis);
		} catch (InterruptedException e) {
			log.error(s);
		}
	}

	private void actionsAfterStop() {
		incomeQueue.clear();
		savingLemmas();
		savingIndexes();
		log.warn(logAboutEachSite());
	}

	private void savingIndexes() {
		long idxSave = System.currentTimeMillis();

		indexRepository.saveAll(indexEntities);
		sleeping(200, "Error sleeping after saving lemmas");
		log.warn("Saving index lasts -  " + (System.currentTimeMillis() - idxSave) + " ms");
		indexEntities.clear();
	}

	private void savingLemmas() {
		long lemmaSave = System.currentTimeMillis();
		lemmaRepository.saveAll(lemmaEntities.values());
		sleeping(200, "Error sleeping after saving lemmas");
		log.warn("Saving lemmas lasts - " + (System.currentTimeMillis() - lemmaSave) + " ms");
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
			countLemmas++;
		}
		return lemmaObj;
	}


	private @NotNull String logAboutEachSite() {
		return countLemmas + " lemmas and "
				+ countIndexes + " indexes saved "
				+ "in DB from site with url "
				+ siteEntity.getUrl();
	}

	public Boolean allowed() {
		return !savingPagesIsDone | incomeQueue.iterator().hasNext();
	}
}