package searchengine.services.lemmatization;

import lombok.Getter;
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

@Getter
@Setter
@Service
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
	private BlockingQueue<SearchIndexEntity> queueForIndexGeneration;
//	private Site site;
	private boolean savingPagesIsDone = false;
	private LemmaEntity lemmaEntity;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;
	private Map<String, LemmaEntity> lemmaEntityMap = StaticVault.lemmaEntityMap;
	private Map<String, Integer> lemmaFreqMap = StaticVault.lemmaFreqMap;
	public static Set<SearchIndexEntity> indexEntitiesSet = StaticVault.indexEntitiesSet;
	private Map<String, Integer> collectedLemmas = new HashMap<>();
	private Map<Map<PageEntity, String>, Float> indexAsVarMap = new HashMap<>();


	public LemmasCollectingService() {
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
		Map<String, LemmaEntity> lemmasOnCurrentPage = new HashMap<>();

		while (true) {
			pageEntity = queue.poll();
			if (pageEntity != null) {
				collectedLemmas = collectLemmas(pageEntity.getContent());

				for (String lemma : collectedLemmas.keySet()) {
					if (indexingStopped) break;

//					увеличиваем частоту, если лемма уже есть в базе
					if (lemmaFreqMap.containsKey(lemma)) {
						var freqOld = lemmaFreqMap.get(lemma);
						lemmaFreqMap.replace(lemma, freqOld + 1);
						lemmaEntityMap.remove(lemma);
						lemmaEntityMap.put(lemma, new LemmaEntity(siteEntity, lemma, lemmaFreqMap.get(lemma)));
					} else {
//					создаем лемму и добавляем во все Map
						lemmaFreqMap.put(lemma, INIT_FREQ);
						/*
						Frequency - Количество страниц, на которых слово встречается хотя бы один раз. Максимальное значение не может
						превышать общее количество слов на сайте
						 */
						lemmaEntityMap.put(lemma, new LemmaEntity(siteEntity, lemma, INIT_FREQ));
					}

//					Добавим лемму в Мап для данной страницы
					lemmasOnCurrentPage.put(lemma, lemmaEntityMap.get(lemma));

//					Формируем поисковый индекс
					for (String lemmaAsSting : collectedLemmas.keySet()) {
						Map<PageEntity, String> key = new HashMap<>();
						key.put(pageEntity, lemmaAsSting);
						indexAsVarMap.put(key, Float.valueOf(collectedLemmas.get(lemmaAsSting)));
					}
				}
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (notAllowed() || indexingStopped) {
//				Сохраняем леммы для данного сайта
				long timeLemmasSaving = System.currentTimeMillis();
				lemmaRepository.saveAll(lemmaEntityMap.values());
				createIndexForThisSite();
				rootLogger.warn(":: " + lemmasOnCurrentPage.size() + " lemmas saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeLemmasSaving) + " ms");
				lemmasOnCurrentPage.clear();
				logger.debug(siteRepository.count() + " sites in DB, " + "site id " + siteRepository.findAll().get(0).getId() + " 129 lemma");
				return;
			}
		}
	}

	private void createIndexForThisSite() {
		for (Map.Entry<Map<PageEntity, String >, Float> entry: indexAsVarMap.entrySet()) {
			Map<PageEntity, String> innerMap = entry.getKey();
			PageEntity pageIdAsEntity = innerMap.keySet().stream().findFirst().get();
			String lemmaAsString = innerMap.values().stream().findFirst().get();
			LemmaEntity lemmaIdAsEntity = lemmaEntityMap.get(lemmaAsString);
			Float rankAsFloat = entry.getValue();
			try {
				queueForIndexGeneration.put(
						new SearchIndexEntity(
								pageIdAsEntity,
								lemmaIdAsEntity,
								rankAsFloat,
								new SearchIndexId(
										pageIdAsEntity.getId(),
										lemmaEntityMap.get(lemmaAsString).getId())));
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
//			indexEntitiesSet.add(
//					new SearchIndexEntity(pageIdAsEntity, lemmaIdAsEntity, rankAsFloat, new SearchIndexId(pageIdAsEntity.getId(), lemmaEntityMap.get(lemmaAsString).getId())));
		}
		rootLogger.warn("drop to index queue done");
	}

	private boolean notAllowed() {
		return savingPagesIsDone && !queue.iterator().hasNext();
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
