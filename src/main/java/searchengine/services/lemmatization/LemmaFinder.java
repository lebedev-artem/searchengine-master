package searchengine.services.lemmatization;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.services.scraping.ScrapingService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;

import static java.lang.Thread.sleep;

@Getter
@Setter
@RequiredArgsConstructor
public class LemmaFinder implements Runnable {
	private static final Logger logger = LogManager.getLogger(LemmaFinder.class);
	private BlockingQueue<PageEntity> queue;
	private final LemmaRepository lemmaRepository;
	private final SearchIndexRepository searchIndexRepository;
	private final LuceneMorphology luceneMorphology;
	private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
	private static final String[] PARTICLES_NAMES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
	private final Future<?> future;
	private Integer siteId;
	private SiteEntity siteEntity;

//	public static LemmaFinder getInstance() throws IOException {
//		LuceneMorphology morphology= new RussianLuceneMorphology();
//		return new LemmaFinder(morphology, getInstance().queue);
//	}

	public LemmaFinder(BlockingQueue<PageEntity> queue, LemmaRepository lemmaRepository, SearchIndexRepository searchIndexRepository, Future<?> future, SiteEntity siteEntity) throws IOException {
		this.luceneMorphology = new RussianLuceneMorphology();
		this.lemmaRepository = lemmaRepository;
		this.searchIndexRepository = searchIndexRepository;
		this.queue = queue;
		this.future = future;
		this.siteEntity = siteEntity;
		siteId = siteEntity.getId();
	}

	public void printEntityContent(@NotNull PageEntity pageEntity) {
		String cleanedHtml = Jsoup.clean(pageEntity.getContent(), Safelist.simpleText());
		System.out.println("--- cleaned txt from queue, length -> " + cleanedHtml.length());
	}

	@Override
	public void run() {
		try {
//			sleep(5_000);
			while (true) {
				sleep(500);
				PageEntity pageEntity = queue.take();
				Map<String, Integer> lemmasOnPage = collectLemmas(pageEntity.getContent());
				for (String lemma : lemmasOnPage.keySet()) {
					if (!lemmaRepository.existsByLemmaAndSiteEntity(lemma, siteEntity)){
						lemmaRepository.save(new LemmaEntity(siteEntity, lemma, 1));
					} else {
						LemmaEntity tempLemmaEntity = lemmaRepository.findByLemmaAndSiteEntity(lemma, siteEntity);
						tempLemmaEntity.setFrequency(tempLemmaEntity.getFrequency() + 1);
						lemmaRepository.save(tempLemmaEntity);
						logger.info("Lemma <" + tempLemmaEntity.getLemma() + "> freq++");
					}
				}
				if (future.isDone() && !queue.iterator().hasNext())
					return;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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

