package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.lemmatization.LemmaFinder;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

	public static final double ratio = 1.85;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final IndexRepository indexRepository;
	private final LemmaFinder lemmaFinder;
	private final SnippetGenerator snippetGenerator;

	Map<String, LemmaEntity> retrievedLemmas = new TreeMap<>();
	Map<String, LemmaEntity> finalLemmas = new TreeMap<>();
	Map<Integer, PageEntity> resultPages = new HashMap<>();
	Map<Integer, PageEntity> rarestPages = new HashMap<>();
	Map<PageEntity, List<String>> pagesLemma = new HashMap<>();
	SearchResponse response = new SearchResponse();
	;

	@Override
	public SearchResponse getSearchResults(@NotNull String query, String siteUrl, Integer offset, Integer limit) {
		if (query.isEmpty()) return emptyQuery();
		Map<PageEntity, Float> totalPagesWithRelevance = new HashMap<>();

		//получаем леммы из запроса
		Map<String, Integer> queryLemmasMap = lemmaFinder.collectLemmas(query);
		Set<String> queryLemmas = new HashSet<>(queryLemmasMap.keySet());

		List<SiteEntity> siteEntities = new ArrayList<>();
		if (siteUrl != null) {
			siteEntities.add(siteRepository.findByUrl(siteUrl));
		} else {
			siteEntities = siteRepository.findAll();
		}

		for (SiteEntity site : siteEntities) {
			String rarestLemma = null;

			//получаем леммы из базы по сайту
			retrievedLemmas = getLemmaEntitiesFromTable(queryLemmas, site);
			if (retrievedLemmas.size() != 0) {
				//Считаем суммарную частоту
				int totalFreq = getTotalFrequency(retrievedLemmas);
				float thresholdFreq = (float) ((totalFreq / retrievedLemmas.size()) * ratio);
				//удаляем слишком частые
				retrievedLemmas = removeMostFrequentlyLemmas(retrievedLemmas, thresholdFreq);
				retrievedLemmas = sortByFrequency(retrievedLemmas);
				//Получаем индекс по самой редкой лемме, и дальше работаем с ним
				rarestLemma = retrievedLemmas.keySet().stream().findFirst().orElse(null);
				if (rarestLemma != null)
					rarestPages = getPageEntitiesLemma(site, rarestLemma);
				printRarestLemma(site, rarestLemma, rarestPages);
			}

			//Продолжаем искать оставшиеся леммы на этих страницах
			finalLemmas = getFinalLemmasMap(rarestLemma, retrievedLemmas);

			Map<Integer, PageEntity> finalPages = new HashMap<>(rarestPages);
			for (String key : finalLemmas.keySet()) {
				Map<Integer, PageEntity> eachIterationPages = getRetainedPages(site, rarestPages, finalPages, key);
				finalPages.clear();
				finalPages.putAll(eachIterationPages);
			}
			resultPages.putAll(finalPages);

			//Считаем релевантность и создаем мап <page, set<Lemma>> для создания сниппетов
			//Итоговый Мап
			Map<PageEntity, Float> pagesRelativeMap = getPagesWithRelevance(resultPages);

			printResultPages("results pages", resultPages);
			totalPagesWithRelevance.putAll(pagesRelativeMap);
			clearAllGlobalVariable();
		}

		totalPagesWithRelevance = sortByRank(totalPagesWithRelevance);
		List<SearchData> totalData = new ArrayList<>();
		for (PageEntity page : totalPagesWithRelevance.keySet()) {
			SearchData searchData = new SearchData();
			searchData.setSiteName(page.getSiteEntity().getName());
			searchData.setSite(page.getSiteEntity().getUrl().replaceFirst("/$", ""));
			searchData.setUri(page.getPath());
			searchData.setSnippet(getSnippet(page));
			searchData.setRelevance(totalPagesWithRelevance.get(page));
			searchData.setTitle(getTitle(page.getContent()));
			totalData.add(searchData);
		}

		response.setData(totalData);
		response.setResult(true);
		response.setCount(totalData.size());

		pagesLemma.clear();

		return response;
	}

	private String getSnippet(PageEntity page){
		String snippet = "...";
		List<String> snippets = snippetGenerator.generateSnippets(page.getContent(), pagesLemma.get(page));
		if (snippets != null){
			for (String s:snippets) {
				snippet = snippet.concat(s);
				snippet.concat("...");
			}
		}
		return snippet;
	}

	private Map<PageEntity, Float> getPagesWithRelevance(Map<Integer, PageEntity> source) {
		if (source.size() != 0) {
			Map<PageEntity, Float> pagesAbsoluteMap = calculateAbsRelevance(source, retrievedLemmas);
			float maxAbsRel = getMaxRank(pagesAbsoluteMap);
			Map<PageEntity, Float> pagesRelativeMap = calculateRelevance(pagesAbsoluteMap, maxAbsRel);
			return pagesRelativeMap;
		}
		return new HashMap<>();
	}

	@NotNull
	private Map<Integer, PageEntity> getRetainedPages(SiteEntity site, Map<Integer, PageEntity> rarestPages, Map<Integer, PageEntity> finalPages, String lemma) {
		LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteEntity(lemma, site);
		Set<IndexEntity> indexSet = indexRepository.findAllByLemmaEntity(lemmaEntity);
		Map<Integer, IndexEntity> pageIdIndex = createPageIdIndexMap(indexSet);

		printLemma(site, lemma, indexSet);

		Map<Integer, PageEntity> eachIterationPages = new HashMap<>();
		pageIdIndex.keySet().forEach(x -> {
			if (finalPages.containsKey(x)) {
				eachIterationPages.put(x, rarestPages.get(x));
			}
		});
		return eachIterationPages;
	}

	private void printResultPages(String results_pages, Map<Integer, PageEntity> resultPages) {
		System.out.println(results_pages);
		resultPages.keySet().forEach(x -> {
			System.out.println(x + " " + resultPages.get(x).getPath());
		});
		System.out.println("-----------------------------------------------------------");
	}

	private void printLemma(SiteEntity site, String key, Set<IndexEntity> indexSet) {
		System.out.println("lemma " + key + " freq - " + lemmaRepository.getByLemmaAndSiteEntity(key, site).getFrequency());
		indexSet.forEach(k -> {
			System.out.println(k.getPageEntity().getId() + " " + k.getPageEntity().getPath());
		});
		System.out.println("-----------------------------------------------------------");
	}

	private void printRarestLemma(SiteEntity site, String rarestLemma, Map<Integer, PageEntity> rarestPages) {
		if (rarestLemma != null && rarestPages != null) {
			System.out.println("Rarest lemma - " + rarestLemma + " freq - " + lemmaRepository.getByLemmaAndSiteEntity(rarestLemma, site).getFrequency());
			printResultPages("pages by rarest lemma", rarestPages);
		}

	}

	private String getTitle(String sourceHtml) {
		Document doc = Jsoup.parse(sourceHtml);
		return doc.title();
	}

	private void clearAllGlobalVariable() {
		retrievedLemmas.clear();
		finalLemmas.clear();
		resultPages.clear();
		rarestPages.clear();
	}

	private float getMaxRank(@NotNull Map<PageEntity, Float> source) {
		Float maxValue = null;
		for (Float value : source.values()) {
			if (maxValue == null || value > maxValue) {
				maxValue = value;
			}
		}
		if (maxValue == null)
			return 1;
		return maxValue;
	}

	private Map<PageEntity, Float> calculateRelevance(Map<PageEntity, Float> source, float maxAbsRel) {
		Map<PageEntity, Float> result = new HashMap<>();
		for (PageEntity p : source.keySet()) {
			result.put(p, source.get(p) / maxAbsRel);
		}
		return result;
	}

	private @NotNull Map<PageEntity, Float> calculateAbsRelevance(Map<Integer, PageEntity> source, Map<String, LemmaEntity> lemmas) {
		Map<PageEntity, Float> result = new HashMap<>();
		for (Integer pId : source.keySet()) {
			List<String> value = new ArrayList<>();
			float absRelPage = (float) 0;
			PageEntity p = source.get(pId);
			for (LemmaEntity l : lemmas.values()) {
				IndexEntity idx = indexRepository.findByLemmaEntityAndPageEntity(l, p);
				if (idx != null) {
					absRelPage += idx.getLemmaRank();
					value.add(l.getLemma());
				}
			}
			pagesLemma.put(p, value);
			result.put(p, absRelPage);
		}
		return result;
	}

	private @NotNull Map<String, LemmaEntity> getFinalLemmasMap(String rarestLemma, Map<String, LemmaEntity> source) {
		Map<String, LemmaEntity> result = new HashMap<>(source);
		if (rarestLemma != null) {
			result.remove(rarestLemma);
			return sortByFrequency(result);
		}
		return new HashMap<>();
	}

	private @NotNull Map<Integer, IndexEntity> createPageIdIndexMap(@NotNull Set<IndexEntity> indexSet) {
		Map<Integer, IndexEntity> result = new HashMap<>();
		indexSet.forEach(x -> result.put(x.getId().getPageId(), x));
		return result;
	}

	private @Nullable Set<IndexEntity> getRarestIndexSet(SiteEntity site, String rarestLemma) {
		LemmaEntity rarestLemmaEntity = lemmaRepository.findByLemmaAndSiteEntity(rarestLemma, site);
		if (rarestLemmaEntity != null)
			return indexRepository.findAllByLemmaEntity(rarestLemmaEntity);
		return null;
	}

	@NotNull
	private static SearchResponse emptyQuery() {
		return new SearchResponse(false, "Задан пустой поисковый запрос", 0, new ArrayList<>());
	}

	private @NotNull Map<Integer, PageEntity> getPageEntitiesLemma(SiteEntity site, @NotNull String rarestLemma) {
		Set<IndexEntity> rarestIndex = getRarestIndexSet(site, rarestLemma);
		Map<Integer, PageEntity> result = new HashMap<>();
		for (IndexEntity obj : rarestIndex) {
			int pageId = obj.getPageEntity().getId();
			PageEntity p = pageRepository.findByIdAndSiteEntity(pageId, site);
			if (p != null) result.put(p.getId(), p);
		}
		return result;
	}

	private @NotNull Map<String, LemmaEntity> removeMostFrequentlyLemmas(Map<String, LemmaEntity> source, float thresholdFreq) {
		Map<String, LemmaEntity> result = new HashMap<>(source);
		if (source.size() > 2) {
			for (String key : source.keySet()) {
				if (source.get(key).getFrequency() > thresholdFreq)
					result.remove(key);
			}
		}
		return new HashMap<>(result);
	}

	private Map<String, LemmaEntity> sortByFrequency(Map<String, LemmaEntity> lemmaEntities) {
		List<Map.Entry<String, LemmaEntity>> list = new ArrayList<>(lemmaEntities.entrySet());
		list.sort(new Comparator<Map.Entry<String, LemmaEntity>>() {
			public int compare(Map.Entry<String, LemmaEntity> o1, Map.Entry<String, LemmaEntity> o2) {
				return o1.getValue().getFrequency() - o2.getValue().getFrequency();
			}
		});

		Map<String, LemmaEntity> resultMap = new LinkedHashMap<>();
		for (Map.Entry<String, LemmaEntity> entry : list) {
			resultMap.put(entry.getKey(), entry.getValue());
		}
		return resultMap;
	}

	private Map<PageEntity, Float> sortByRank(Map<PageEntity, Float> source) {
		Map<PageEntity, Float> sortedMap = source.entrySet()
				.stream()
				.sorted(Map.Entry.<PageEntity, Float>comparingByValue()
						.reversed())
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(oldValue, newValue) -> oldValue,
						LinkedHashMap::new));
		return sortedMap;
	}


	private @NotNull TreeMap<String, LemmaEntity> getLemmaEntitiesFromTable(@NotNull Set<String> queryLemmas, SiteEntity site) {
		Map<String, LemmaEntity> resultSet = new HashMap<>();
		for (String lemma : queryLemmas) {
			LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteEntity(lemma, site);
			if (lemmaEntity == null) continue;
			resultSet.put(lemma, lemmaEntity);
		}
		return new TreeMap<>(resultSet);
	}

	private Integer getTotalFrequency(Map<String, LemmaEntity> lemmaEntities) {
		int result = 0;
		for (String key : lemmaEntities.keySet()) {
			result += lemmaEntities.get(key).getFrequency();
		}
		return result;
	}
}
