package searchengine.services.lemmatization;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SearchIndexEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Service
@RequiredArgsConstructor
public class LemmaFinderPageable{
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
//	private BlockingQueue<PageEntity> queue;
	private LuceneMorphology luceneMorphology;
//	private SiteEntity siteEntity;
	private Integer siteId;


//	public LemmaFinderPageable(LemmaRepository lemmaRepository, SearchIndexRepository searchIndexRepository,
//	                           PageRepository pageRepository, SiteRepository siteRepository,
//	                           SiteEntity siteEntity, Integer siteId) throws IOException {
//		this.lemmaRepository = lemmaRepository;
//		this.searchIndexRepository = searchIndexRepository;
//		this.pageRepository = pageRepository;
//		this.siteRepository = siteRepository;
//		this.siteEntity = siteEntity;
//		this.siteId = siteId;
//		this.luceneMorphology = new RussianLuceneMorphology();
//	}
//
	@Autowired
	public void setLuceneMorphology() {
		try {
			this.luceneMorphology = new RussianLuceneMorphology();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
//	@Autowired
//	public void setSiteEntity(SiteEntity siteEntity) {
//		this.siteEntity = siteEntity;
//	}
//
//	@Autowired
//	public void setSiteId(Integer siteId) {
//		this.siteId = siteId;
//	}


	public void runThroughQueue(@NotNull BlockingQueue<PageEntity> queueOfPages, Future<?> futureForScrapingSite){
		Map<String, LemmaEntity> lemmas = new HashMap<>();
		Set<SearchIndexEntity> searchIndexEntityMap = new HashSet<>();

		try {
//			sleep(5_000);
			while (true) {
				PageEntity pageEntity = queueOfPages.take();
				SiteEntity siteEntity = siteRepository.findById(pageEntity.getSiteEntity().getId());
				Map<String, Integer> lemmasOnPage = collectLemmas(pageEntity.getContent());
				for (String lemma : lemmasOnPage.keySet()) {
					int freq;

					if (!lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity) && !lemmas.containsKey(lemma)){
						freq = 1;
					} else {
						if (lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity)){
							freq = lemmaRepository.findByLemmaAndSiteEntity(lemma, siteEntity).getFrequency() + 1;
						} else {
							freq = lemmas.get(lemma).getFrequency() + 1;
						}
					}

					LemmaEntity lemmaEntity = new LemmaEntity(siteEntity, lemma, freq);
					lemmas.put(lemma, lemmaEntity);
					searchIndexEntityMap.add(new SearchIndexEntity(pageEntity, lemmaEntity, lemmasOnPage.get(lemma)));
				}
				if (lemmas.size() > 100){
					lemmaRepository.saveAll(lemmas.values());
					for (SearchIndexEntity see : searchIndexEntityMap) {
						searchIndexRepository.save(see);
					}

					lemmas.clear();
					searchIndexEntityMap.clear();

				}
				if (futureForScrapingSite.isDone() && !queueOfPages.iterator().hasNext())
					return;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void runThroughPageable(Integer siteId, SiteEntity siteEntity) {
		try {
//			sleep(5_000);
			while (true) {
				sleep(1_000);

				Set<PageEntity> pagesCOfCurrentSite = new HashSet<>();
				long time = System.currentTimeMillis();

				int pageNumber = 0; // номер первой страницы
				int pageSize = 50; // количество записей на странице
//				Sort sort = Sort.by(Sort.Direction.ASC, "id"); // сортировка по полю "id" в порядке возрастания
//				Pageable pageable = PageRequest.of(pageNumber, pageSize, sort); // первая страница, содержащая 10 записей, отсортированных по полю "id"
				Pageable pageable = PageRequest.of(pageNumber, pageSize);
				Page<PageEntity> page = pageRepository.findAll(pageable); // получение первой страницы записей

				List<PageEntity> entitiesList = page.getContent();
				while (!entitiesList.isEmpty()) { // пока есть записи
					for (PageEntity entity : entitiesList) {
						Map<String, LemmaEntity> lemmasOnPage = new HashMap<>();
						Map<String, Integer> collectedLemmas = collectLemmas(entity.getContent());

						for (String lemma : collectedLemmas.keySet()) {
							if (!lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity)) {
								lemmasOnPage.put(lemma, new LemmaEntity(siteEntity, lemma, INIT_FREQ));
							} else {
								updateFreqOfLemma(siteEntity, lemma);
							}
						}
						rootLogger.info("Now will save " + lemmasOnPage.size() + " lemmas to DB");
						lemmaRepository.saveAll(lemmasOnPage.values());
						lemmasOnPage.clear();
					}
					if (!page.hasNext()) {
						break;
					}

					pageable = page.nextPageable();
					page = pageRepository.findAll(pageable);
					entitiesList = page.getContent();
				}


				logger.warn("Finding all pages takes - " + (System.currentTimeMillis() - time) + " ms");
				break;

			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void updateFreqOfLemma(SiteEntity siteEntity, String lemma) {
		LemmaEntity lemmaNeedsUpdateFreq = lemmaRepository.findByLemmaAndSiteEntity(lemma, siteEntity);
		lemmaNeedsUpdateFreq.setFrequency(lemmaNeedsUpdateFreq.getFrequency() + 1);
		lemmaRepository.save(lemmaNeedsUpdateFreq);
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

	private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
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

	private String[] arrayContainsRussianWords(String text) {
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


/*
 else {
						LemmaEntity lemmaForFreqInc = lemmaRepository.findByLemmaAndSiteEntity(lemma, siteEntity);
						lemmaForFreqInc.setFrequency(lemmaForFreqInc.getFrequency() + 1);
						lemmaRepository.save(lemmaForFreqInc);
						logger.info("Lemma <" + lemmaForFreqInc.getLemma() + "> freq increase");
					}
@Transactional
public BankAccount updateRate(Long id, BigDecimal rate) {
  BankAccount account = repo.findById(id).orElseThrow(NPE::new);
  account.setRate(rate);
  return repo.save(account);
}
--->
@Transactional
public BankAccount updateRate(Long id, BigDecimal rate) {
  BankAccount account = repo.findById(id).orElseThrow(NPE::new);
  account.setRate(rate);
  return account;
}
 */
