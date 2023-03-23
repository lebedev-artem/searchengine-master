package searchengine.services.lemmatization;

import lombok.EqualsAndHashCode;
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
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
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
	IndexRepository indexRepository;
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
	private BlockingQueue<Integer> incomeQueue;
	private BlockingQueue<IndexEntity> queueOfSearchIndexEntities;
	private BlockingQueue<LemmaEntity> queueOfLemmaEntityToSaveEntities;
	private boolean savingPagesIsDone = false;
	private LemmaEntity lemmaEntity;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;
	private IndexEntity indexEntity;
	private Set<LemmaEntity> lemmaEntities = new HashSet<>();
	private Set<IndexEntity> indexEntities = new HashSet<>();
	private Map<String, Integer> collectedLemmas = new HashMap<>();
	public static Map<String, LemmaEntity> lemmaEntitiesStaticMap = StaticVault.lemmaEntitiesMap;
	private Map<Map<PageEntity, String>, Integer> idxMap = new HashMap<>();
	private BlockingQueue<Map.Entry<Map<PageEntity, String>, Integer>> queueIdx;


	public void lemmasIndexGeneration() {
		savingPagesIsDone = false;

		while (true) {
			Integer id = incomeQueue.poll();

			if (id != null) {
				pageEntity = pageRepository.getReferenceById(id);

				collectedLemmas = collectLemmas(pageEntity.getContent());
				long collecting = System.currentTimeMillis();
				for (String lemma : collectedLemmas.keySet()) {
					if (indexingStopped) break;

					int rank = collectedLemmas.get(lemma);
					lemmaEntity = new LemmaEntity(siteEntity, lemma, INIT_FREQ);

						if (lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity)) {
							lemmaEntity = lemmaRepository.findByLemmaAndSiteEntity(lemma, siteEntity);
							int oldFreq = lemmaEntity.getFrequency();
							lemmaEntity.setFrequency(oldFreq + 1);
						}

//					else {
//						lemmaEntities.add(lemmaEntity);
//						lemmaEntitiesStaticMap.put(lemma, lemmaEntity);
//						newLemmas++;
//					}
//					if (lemmaEntitiesStaticMap.containsKey(lemma)) {
//						int oldFreq = lemmaEntitiesStaticMap.get(lemma).getFrequency();
//						lemmaEntitiesStaticMap.get(lemma).setFrequency(oldFreq + 1);
//						lemmaEntity = lemmaEntitiesStaticMap.get(lemma);
//						oldLemmas++;
//					} else {
//						lemmaEntities.add(lemmaEntity);
//						lemmaEntitiesStaticMap.put(lemma, lemmaEntity);
//						newLemmas++;
//					}

//					Map<PageEntity, String> idxId = new HashMap<>();
//					idxId.put(pageEntity, lemma);
//					idxMap.put(idxId, rank);

					IndexEntity searchIndexEntity = new IndexEntity (new IndexEntity.Id(), pageEntity, lemmaEntity, rank);
//					queueOfSearchIndexEntities.add(searchIndexEntity);
//					searchIndexEntity.setLemmaEntity(lemmaEntity);
					indexEntities.add(searchIndexEntity);
				}

				long saving = System.currentTimeMillis();
				indexRepository.saveAll(indexEntities);
				logger.warn(
						indexEntities.size() + " lemmas from page " + pageEntity.getId()
						+ " collected in " + + (System.currentTimeMillis() - collecting) + " ms"
						+ " and saved in " + (System.currentTimeMillis() - saving) + " ms");
				indexEntities.clear();


//				queueOfLemmaEntityToSaveEntities.addAll(lemmaEntities);
//				queueOfSearchIndexEntities.addAll(searchIndexEntities.values());
//				logger.warn(searchIndexEntities.size() + " added to outcome queue");
//				for (LemmaEntity lE : lemmaEntities) {
//					try {
//						queueOfLemmaEntityToSaveEntities.put(lE);
//					} catch (InterruptedException e) {
//						throw new RuntimeException(e);
//					}
//				}

//				searchIndexEntities.forEach(sIE -> {
//					try {
//						queueOfSearchIndexEntities.put(sIE);
//					} catch (InterruptedException e) {
//						throw new RuntimeException(e);
//					}
//				});
//
//				logger.warn("pageEntity id - " + pageEntity.getId() + "| new lemmas - " + newLemmas + "| old lemmas - " + oldLemmas);
//				lemmaEntities.clear();
//				searchIndexEntities.clear();

			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (previousStepDoneAndQueueIsEmpty() || indexingStopped) {
				indexRepository.saveAll(indexEntities);
//				queue.clear();
//				queueOfSearchIndexEntities.clear();
//				queueOfLemmaEntityToSaveEntities.clear();
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


	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		LemmasCollectingService that = (LemmasCollectingService) o;
		return indexEntity.equals(that.indexEntity)
				&& indexEntity.getLemmaEntity().equals(that.indexEntity.getLemmaEntity())
				&& indexEntity.getPageEntity().equals(that.indexEntity.getPageEntity())
				&& indexEntity.getLemmaEntity().getLemma().equals(that.getLemmaEntity().getLemma());
	}

	@Override
	public int hashCode() {
		if (lemmaEntity == null) {
			return Objects.hash(pageEntity);
		} else {
			return Objects.hash(pageEntity, lemmaEntity.getLemma());
		}
	}

	private boolean previousStepDoneAndQueueIsEmpty() {
		return savingPagesIsDone && !incomeQueue.iterator().hasNext();
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