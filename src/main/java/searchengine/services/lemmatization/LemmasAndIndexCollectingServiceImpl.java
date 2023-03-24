package searchengine.services.lemmatization;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.services.indexing.IndexServiceImpl;

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
				collectedLemmas = lemmaFinder.collectLemmas(pageEntity.getContent());

				long startPageTime = System.currentTimeMillis();
				for (String lemma : collectedLemmas.keySet()) {

					int rank = collectedLemmas.get(lemma);
					lemmaEntity = createLemmaEntity(lemma);
					IndexEntity indexEntity = new IndexEntity(new IndexEntity.Id(), pageEntity, lemmaEntity, rank);
					indexEntities.add(indexEntity);
				}
				indexRepository.saveAll(indexEntities);
				log.info(logAboutPage(pageId, startPageTime));
				indexEntities.clear();

			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		log.warn(logAboutEachSite(startSiteTime));
	}

	public LemmaEntity createLemmaEntity(String lemma) {
		LemmaEntity lemmaObj;
		if (lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity)) {
			lemmaObj = increaseLemmaFrequency(lemma);
		} else {
			lemmaObj = new LemmaEntity(siteEntity, lemma, INIT_FREQ);
		}
		return lemmaObj;
	}

	public LemmaEntity increaseLemmaFrequency(String lemma) {
		LemmaEntity lemmaObj = lemmaRepository.findByLemmaAndSiteEntity(lemma, siteEntity);
		int oldFreq = lemmaObj.getFrequency();
		lemmaObj.setFrequency(oldFreq + 1);
		return lemmaObj;
	}

	private @NotNull String logAboutPage(Integer pageId, long eachPageTime) {
		return indexEntities.size()
				+ " lemmas from page " + pageId
				+ " collected and saved in " + +(System.currentTimeMillis() - eachPageTime) + " ms";
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