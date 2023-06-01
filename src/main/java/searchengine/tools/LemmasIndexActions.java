package searchengine.tools;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import java.util.*;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.sleep;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
public class LemmasIndexActions {

	private Boolean enabled = true;
	private Integer countPages = 0;
	private Integer countLemmas = 0;
	private Integer countIndexes = 0;
	private SiteEntity siteEntity;
	private IndexEntity indexEntity;
	private boolean crawlingIsDone = false;
	private BlockingQueue<PageEntity> incomeQueue;
	private Set<IndexEntity> indexEntities = new HashSet<>();
	private Map<String, Integer> collectedLemmas = new HashMap<>();
	private Map<String, LemmaEntity> lemmaEntities = new HashMap<>();
	private final LemmaFinder lemmaFinder;
	private final Integer INIT_FREQ = 1;
	private final PageRepository pageRepository;
	private final IndexRepository indexRepository;
	private final LemmaRepository lemmaRepository;

	public void startCollecting() {
		while (allowed()) {

			if (!enabled) {
				actionsAfterStop();
				return;
			}

			PageEntity pageEntity = incomeQueue.poll();
			if (pageEntity != null) {
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

	private @NotNull String logAboutEachSite() {
		return countLemmas + " lemmas and "
				+ countIndexes + " indexes saved "
				+ "in DB from site with url "
				+ siteEntity.getUrl();
	}

	public Boolean allowed() {
		return !crawlingIsDone | incomeQueue.iterator().hasNext();
	}


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
}