package searchengine.services.lemmatization;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.*;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;

@Getter
@Setter
@Service
public class LemmasIndexService {
	@Autowired
	LemmaRepository lemmaRepository;
	@Autowired
	SearchIndexRepository searchIndexRepository;
	@Autowired
	PageRepository pageRepository;
	@Autowired
	SiteRepository siteRepository;
	private static final Integer INIT_FREQ = 1;
	private static final Logger logger = LogManager.getLogger(LemmaFinder.class);
	private static final Logger rootLogger = LogManager.getRootLogger();
	private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
	private static final String[] PARTICLES_NAMES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
	private volatile boolean indexingStopped = false;
	private LuceneMorphology luceneMorphology;
	private BlockingQueue<PageEntity> queue;
	private Site site;
	private boolean scrapingFutureIsDone = false;

	public LemmasIndexService() {
	}

	@Autowired
	public void setLuceneMorphology() {
		try {
			this.luceneMorphology = new RussianLuceneMorphology();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void lemmasIndexGeneration() {
		Map<LemmaEntity, String> lemmas = new HashMap<>();
		Map<SearchIndexEntity, LemmaEntity> searchIndexEntityLemmaEntityMap = new HashMap<>();
		Set<SearchIndexEntity> searchIndexEntityMap = new HashSet<>();
		long timeLemmas = System.currentTimeMillis();

		waitUntilFirstPageWillBeSaved();

			while (true) {
				PageEntity pageEntity = queue.poll();
				if (pageEntity != null){
					SiteEntity siteEntity = siteRepository.findById(pageEntity.getSiteEntity().getId());
					Map<String, Integer> lemmasOnPage = collectLemmas(pageEntity.getContent());
					for (String lemma : lemmasOnPage.keySet()) {
						if (indexingStopped) break;
						LemmaEntity lemmaEntity;
						if (!lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity)) {
							lemmaEntity = new LemmaEntity(siteEntity, lemma, INIT_FREQ);
						} else lemmaEntity = updateFreqOfLemma(siteEntity, lemma);

						lemmaRepository.save(lemmaEntity);

						SearchIndexEntity searchIndexEntity = new SearchIndexEntity(pageEntity, lemmaEntity, lemmasOnPage.get(lemma), new SearchIndexId(pageEntity.getId(), lemmaEntity.getId()));
						searchIndexRepository.save(searchIndexEntity);
					}
				} try {
					Thread.sleep(100);
				} catch (InterruptedException e){
					e.printStackTrace();
				}

				if (notAllowed() || indexingStopped) {
					rootLogger.info("::::: Lemmas of " + site.getName() + " finding finished in " + (System.currentTimeMillis() - timeLemmas) + " ms");
					rootLogger.warn("::::: " + lemmaRepository.count() + " lemmas in DB, site -> " + site.getName());
					rootLogger.warn("::::: " + searchIndexRepository.count() + " indexes in DB, site -> " + site.getName());

					return;
				}
			}
	}

	private void waitUntilFirstPageWillBeSaved(){
		while (true){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			if (pageRepository.count() > 0)
				break;
		}
	}

	private boolean notAllowed() {
		return scrapingFutureIsDone && !queue.iterator().hasNext();
	}

	private LemmaEntity updateFreqOfLemma(SiteEntity siteEntity, String lemma) {
		LemmaEntity lemmaNeedsUpdateFreq;
		try {
			lemmaNeedsUpdateFreq = lemmaRepository.findByLemmaAndSiteEntity(lemma, siteEntity);
		} catch (IncorrectResultSizeDataAccessException e) {
			e.printStackTrace();
		} finally {
			lemmaNeedsUpdateFreq = lemmaRepository.findFirstByLemmaAndSiteEntity(lemma, siteEntity);

		}

		lemmaNeedsUpdateFreq.setFrequency(lemmaNeedsUpdateFreq.getFrequency() + 1);
		lemmaRepository.save(lemmaNeedsUpdateFreq);
		return lemmaNeedsUpdateFreq;
	}

	public Map<String, Integer> collectLemmas(String text) {
		String[] words = arrayContainsRussianWords(text);
		HashMap<String, Integer> lemmas = new HashMap<>();
		for (String word : words) {
			if (word.isBlank() | ((word.length() == 1) && (!word.toLowerCase(Locale.ROOT).equals("я")))) {
				continue;
			}

			List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
			if (anyWordBaseBelongToParticle(wordBaseForms)) {
				continue;
			}

			List<String> normalForms = luceneMorphology.getNormalForms(word);
			if (normalForms.isEmpty()) {
				continue;
			}

			String normalWord = normalForms.get(0);

			if (lemmas.containsKey(normalWord)) {
				lemmas.put(normalWord, lemmas.get(normalWord) + 1);
			} else {
				lemmas.put(normalWord, 1);
			}
		}
		return lemmas;
	}

	public Set<String> getLemmaSet(String text) {
		String[] textArray = arrayContainsRussianWords(text);
		Set<String> lemmaSet = new HashSet<>();
		for (String word : textArray) {
			if (!word.isEmpty() && isCorrectWordForm(word)) {
				List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
				if (anyWordBaseBelongToParticle(wordBaseForms)) {
					continue;
				}
				lemmaSet.addAll(luceneMorphology.getNormalForms(word));
			}
		}
		return lemmaSet;
	}

	private boolean anyWordBaseBelongToParticle(@NotNull List<String> wordBaseForms) {
		return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
	}

	private boolean hasParticleProperty(String wordBase) {
		for (String property : PARTICLES_NAMES) {
			if (wordBase.toUpperCase().contains(property)) {
				return true;
			}
		}
		return false;
	}

	private String @NotNull [] arrayContainsRussianWords(@NotNull String text) {
		return text.toLowerCase(Locale.ROOT)
				.replaceAll("([^а-я\\s])", " ")
				.trim()
				.split("\\s+");
	}

	private boolean isCorrectWordForm(String word) {
		List<String> wordInfo = luceneMorphology.getMorphInfo(word);
		for (String morphInfo : wordInfo) {
			if (morphInfo.matches(WORD_TYPE_REGEX)) {
				return false;
			}
		}
		return true;
	}
}
