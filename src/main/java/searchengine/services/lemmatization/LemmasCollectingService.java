package searchengine.services.lemmatization;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.bucket.LemmaFinder;
import searchengine.model.*;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.stuff.StaticVault;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Service
@NoArgsConstructor
public class LemmasCollectingService {
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
	private BlockingQueue<SearchIndexEntity> queueOfSearchIndexEntities;
	private boolean savingPagesIsDone = false;
	private LemmaEntity lemmaEntity;
	SearchIndexEntity searchIndexEntity;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;
	private Map<String, LemmaEntity> lemmaEntities = new HashMap<>();
	private Map<String, SearchIndexEntity> searchIndexEntities = new HashMap<>();
	private Map<String, Integer> collectedLemmas = new HashMap<>();


	public void lemmasIndexGeneration() {
		savingPagesIsDone = false;
		while (true) {
			pageEntity = queue.poll();
			if (pageEntity != null) {
				System.out.println("lemma income queue = " + queue.size());
				collectedLemmas = collectLemmas(pageEntity.getContent());

				for (String lemma : collectedLemmas.keySet()) {
					if (indexingStopped) break;

					int rank = collectedLemmas.get(lemma);
					lemmaEntity = new LemmaEntity(siteEntity, lemma, INIT_FREQ);

					searchIndexEntity = new SearchIndexEntity
							(pageEntity, lemmaEntity, rank, new SearchIndexId
									(pageEntity.getId(), lemmaEntity.getId()));

					if (lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity)) {
						lemmaEntity = lemmaRepository.getByLemmaAndSiteEntity(lemma, siteEntity);
						int oldFreq = lemmaEntity.getFrequency();
						lemmaEntity.setFrequency(oldFreq + 1);
						//попробовать закомментировать
//						lemmaRepository.save(lemmaEntity);
						searchIndexEntity.setLemmaEntity(lemmaEntity);
					} else {
						lemmaEntities.put(lemma, lemmaEntity);
					}

					searchIndexEntities.put(lemma, searchIndexEntity);

				}

				//Когда лемматизация страницы закончена, сохраним леммы пачкой и отправим индекс
				lemmaRepository.saveAll(lemmaEntities.values());
				searchIndexEntities.forEach((l, searchIndexEntity) -> {
					if (searchIndexEntity.getLemmaEntity().getId() == null)
						searchIndexEntity.setLemmaEntity(lemmaEntities.get(l));
					try {
						queueOfSearchIndexEntities.put(searchIndexEntity);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}

				});
				System.out.println("index outcome queue = " + queueOfSearchIndexEntities.size());
				lemmaEntities.values().clear();
				searchIndexEntities.values().clear();

			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (previousStepDoneAndQueueIsEmpty() || indexingStopped) {
				return;
			}
		}
	}

//	private void saveLemmaEntitiesOfSite() {
//		long timeLemmasSaving = System.currentTimeMillis();
////		lemmaRepository.saveAll(lemmaEntitiesStaticMap.values());
////		rootLogger.warn(":: " + lemmaRepository.countBySiteEntity(siteEntity) + " lemmas saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeLemmasSaving) + " ms");
//	}

//	private void dropSearchIndexEntitiesToQueue() {
//		searchIndexEntitiesStaticMap.forEach(searchIndexEntity -> {
//			try {
//				queueOfSearchIndexEntities.put(searchIndexEntity);
//			} catch (InterruptedException e) {
//				throw new RuntimeException(e);
//			}
//		});
//	}

	private boolean previousStepDoneAndQueueIsEmpty() {
		return savingPagesIsDone && !queue.iterator().hasNext();
	}

	@Autowired
	public void setLuceneMorphology() {
		try {
			this.luceneMorphology = new RussianLuceneMorphology();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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