package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.core.env.Environment;
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
import searchengine.services.SearchService;
import searchengine.tools.LemmaFinder;
import searchengine.tools.SnippetGenerator;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static searchengine.tools.UrlFormatter.getShortUrl;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

	private Integer totalPagesCount = 0;
	private String rarestLemma = null;
	private Map<String, LemmaEntity> nextStepLemmas = new HashMap<>();
	public final double RATIO = 1.80;
	private final LemmaFinder lemmaFinder;
	private final SnippetGenerator snippetGenerator;
	private final Environment environment;
	private final Map<PageEntity, Float> returnPagesWithRelevance = new HashMap<>();
	private final PageRepository pageRepository;
	private final SiteRepository siteRepository;
	private final LemmaRepository lemmaRepository;
	private final IndexRepository indexRepository;

	@Override
	public SearchResponse getSearchResults(@NotNull String query, String siteUrl, Integer offset, Integer limit) {
		log.warn("Mapping /search executed. " + "query - " + query + ", url - " + siteUrl + ", offset - " + offset + ", limit - " + limit);
		if (query.isEmpty()) return emptyQuery();

		Set<String> queryLemmas = lemmaFinder.getLemmaSet(query);
		List<SiteEntity> siteEntities = getSiteEntities(siteUrl);

		for (SiteEntity site : siteEntities) {
			Map<String, LemmaEntity> retrievedLemmas = getLemmaEntitiesFromTable(queryLemmas, site);

			if (allWordFromQueryFondOnSite(queryLemmas, retrievedLemmas)) {
				List<PageEntity> pagesByRarestLemma = getPagesByRarestLemma(site, retrievedLemmas);
				List<PageEntity> finalPages = getFinalPagesList(site, pagesByRarestLemma);
				returnPagesWithRelevance.putAll(calculateAbsoluteRelevance(finalPages, nextStepLemmas));
			}
		}

		Map<PageEntity, Float> subsetMap = getSortedSubsetMap(getReturnPagesWithRelevance(returnPagesWithRelevance), limit, offset);
		List<SearchData> totalData = getTotalData(queryLemmas, subsetMap);

		return getResponse(totalData);
	}

	@NotNull
	private List<SearchData> getTotalData(Set<String> queryLemmas, @NotNull Map<PageEntity, Float> subsetMap) {
		List<SearchData> totalData = new ArrayList<>();

		for (PageEntity page : subsetMap.keySet()) {
			SearchData searchData = getSearchData(queryLemmas, subsetMap, page);
			totalData.add(searchData);
		}
		return totalData;
	}

	private SearchResponse getResponse(List<SearchData> totalData) {
		SearchResponse result = SearchResponse.builder()
				.result(true)
				.count(totalPagesCount)
				.data(totalData)
				.build();
		returnPagesWithRelevance.clear();
		totalPagesCount = 0;
		return result;
	}

	private SearchData getSearchData(Set<String> queryLemmas, @NotNull Map<PageEntity, Float> subsetMap, @NotNull PageEntity page) {
		return SearchData.builder()
				.uri(page.getPath())
				.siteName(page.getSiteEntity().getName())
				.site(getShortUrl(page.getSiteEntity().getUrl()).replaceFirst("/$", ""))
				.snippet(getSnippet(page, new ArrayList<>(queryLemmas)))
				.relevance(subsetMap.get(page))
				.title(getTitle(page.getContent())).build();
	}

	private Map<PageEntity, Float> getReturnPagesWithRelevance(Map<PageEntity, Float> pages) {
		Map<PageEntity, Float> result;
		float maxRank = getMaxRank(pages);
		Map<PageEntity, Float> pagesWRel = calculateRelevance(pages, maxRank);
		result = sortByRank(pagesWRel);
		return result;
	}

	private Boolean allWordFromQueryFondOnSite(Set<String> query, Map<String, LemmaEntity> fondLemmas) {
		boolean result = true;
		if (fondLemmas.size() == 0) {
			return false;
		}
		if (Objects.equals(environment.getProperty("user-settings.return-zero-pages-if-not-all-lemmas-found"), "true")) {
			for (String lemma : query) {
				if (!fondLemmas.containsKey(lemma)) {
					result = false;
					System.out.println("Not all queried lemmas exists on site. Skip other result from this site");
					break;
				}
			}
		}
		return result;
	}


	@NotNull
	private List<PageEntity> getFinalPagesList(SiteEntity site, List<PageEntity> prevStepPages) {
		Map<String, LemmaEntity> finalLemmas = getFinalSortedLemmasMap(rarestLemma, nextStepLemmas);

		List<PageEntity> finalPages = new ArrayList<>(prevStepPages);
		for (String desiredLemma : finalLemmas.keySet()) {
			List<PageEntity> eachIterationPages = getRetainedPages(site, finalPages, desiredLemma);
			finalPages.clear();
			finalPages.addAll(eachIterationPages);
		}
		totalPagesCount += finalPages.size();
		System.out.println("After retain " + finalPages.size() + " pages remained");

		return finalPages;
	}

	private List<PageEntity> getPagesByRarestLemma(SiteEntity site, Map<String, LemmaEntity> retrievedLemmas) {
		int totalFreq = getTotalFrequency(retrievedLemmas);
		double ratio = 1;
		if (Objects.equals(environment.getProperty("user-settings.exclude-most-frequently-lemmas"), "true")){
			ratio = RATIO;
		}
		float thresholdFreq = (float) ((totalFreq / retrievedLemmas.size()) * ratio);

		nextStepLemmas = removeMostFrequentlyLemmas(retrievedLemmas, thresholdFreq);
		nextStepLemmas = sortByFrequency(nextStepLemmas);

		rarestLemma = nextStepLemmas.keySet().stream().findFirst().orElse(null);
		System.out.println("Rarest lemma is - " + rarestLemma);

		List<PageEntity> result = null;
		if (rarestLemma != null) {
			List<Integer> pageIds = getPageEntitiesIdsByRarestLemma(site);
			result = pageRepository.findAllByIdIn(pageIds);
			System.out.println("From rarest lemma uploaded " + result.size() + " pages");
		}

		return result;
	}

	private List<SiteEntity> getSiteEntities(String siteUrl) {
		return siteUrl != null
				? Collections.singletonList(siteRepository.findByUrl(siteUrl))
				: siteRepository.findAll();
	}

	@NotNull
	private List<Integer> getPageEntitiesIdsByRarestLemma(SiteEntity site) {
		return Objects.requireNonNull(getRarestIndexSet(site, rarestLemma))
				.stream()
				.map(IndexEntity::getPageEntity)
				.map(PageEntity::getId)
				.collect(Collectors.toList());
	}

	public Map<PageEntity, Float> getSortedSubsetMap(@NotNull Map<PageEntity, Float> source, int limit, int offset) {
		//Sort Map in descending order of Float values
		List<Map.Entry<PageEntity, Float>> sortedEntries = new ArrayList<>(source.entrySet());
		sortedEntries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

		//Select a subset of elements
		int endIndex = Math.min(offset + limit, source.size());
		List<Map.Entry<PageEntity, Float>> subsetEntries = sortedEntries.subList(offset, endIndex);

		//Create a new Map with the selected elements
		Map<PageEntity, Float> result = new LinkedHashMap<>();
		System.out.println("Next pages will show as results");
		for (Map.Entry<PageEntity, Float> entry : subsetEntries) {
			result.put(entry.getKey(), entry.getValue());
			System.out.println("Page id " + entry.getKey().getId() + " with path " + entry.getKey().getPath() + " with rank " + entry.getValue());
		}
		return result;
	}

	private @NotNull Map<String, LemmaEntity> getLemmaEntitiesFromTable(@NotNull Set<String> queryLemmas, @NotNull SiteEntity site) {

		Map<String, LemmaEntity> result = queryLemmas.stream()
				.map(lemma -> lemmaRepository.findByLemmaAndSiteEntity(lemma, site))
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(LemmaEntity::getLemma, Function.identity()));
		System.out.println("Site - " + site.getUrl() + " precessing now");
		System.out.println("Retrieved lemmas count - " + result.size());
		return result;
	}

	private @NotNull Integer getTotalFrequency(@NotNull Map<String, LemmaEntity> lemmaEntities) {
		return lemmaEntities.values().stream()
				.mapToInt(LemmaEntity::getFrequency)
				.sum();
	}

	private Map<String, LemmaEntity> getFinalSortedLemmasMap(String rarestLemma, @NotNull Map<String, LemmaEntity> source) {
		return source.entrySet().stream()
				.filter(entry -> !entry.getKey().equals(rarestLemma))
				.sorted(Map.Entry.comparingByValue(Comparator.comparingInt(LemmaEntity::getFrequency)))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
	}

	private Map<String, LemmaEntity> sortByFrequency(@NotNull Map<String, LemmaEntity> lemmaEntities) {
		List<Map.Entry<String, LemmaEntity>> list = new ArrayList<>(lemmaEntities.entrySet());
		list.sort(Comparator.comparingInt(o -> o.getValue().getFrequency()));
		return list.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(oldValue, newValue) -> oldValue, LinkedHashMap::new));
	}

	private @NotNull List<PageEntity> getRetainedPages(SiteEntity site, @NotNull List<PageEntity> finalPages, String lemma) {
		LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteEntity(lemma, site);
		Set<IndexEntity> indexSet = indexRepository.findAllByLemmaEntity(lemmaEntity);
		return finalPages.stream()
				.filter(pageEntity -> indexSet.stream()
						.anyMatch(index -> Objects.equals(index.getPageEntity().getId(), pageEntity.getId())))
				.toList();
	}

	private @NotNull Map<PageEntity, Float> calculateAbsoluteRelevance(@NotNull List<PageEntity> source, Map<String, LemmaEntity> lemmas) {
		Map<PageEntity, Float> result = new HashMap<>();
		for (PageEntity p : source) {
			float absRelPage = getSumOfLemmasRankFromPage(lemmas, p);
			result.put(p, absRelPage);
		}
		return result;
	}

	private float getSumOfLemmasRankFromPage(@NotNull Map<String, LemmaEntity> lemmas, PageEntity p) {
		float result = (float) 0;
		for (LemmaEntity l : lemmas.values()) {
			IndexEntity idx = indexRepository.findByLemmaEntityAndPageEntity(l, p);
			if (idx != null) {
				result += idx.getLemmaRank();
			}
		}
		return result;
	}

	private float getMaxRank(@NotNull Map<PageEntity, Float> source) {
		Float maxValue = source.values().stream().max(Comparator.naturalOrder()).orElse(null);
		return maxValue == null ? 1 : maxValue;
	}

	private Map<PageEntity, Float> calculateRelevance(@NotNull Map<PageEntity, Float> source, float maxAbsRel) {
		return source.keySet()
				.stream()
				.collect(Collectors.toMap(p -> p, p -> source.get(p) / maxAbsRel, (a, b) -> b));
	}

	private Map<PageEntity, Float> sortByRank(@NotNull Map<PageEntity, Float> source) {
		return source.entrySet()
				.stream()
				.sorted(Map.Entry.<PageEntity, Float>comparingByValue()
						.reversed())
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(oldValue, newValue) -> oldValue,
						LinkedHashMap::new));
	}

	private String getSnippet(@NotNull PageEntity page, List<String> lemmas) {
		snippetGenerator.setText(page.getContent());
		snippetGenerator.setQueryWords(lemmas);
		return snippetGenerator.generateSnippets();
	}

	private @NotNull String getTitle(String sourceHtml) {
		Document doc = Jsoup.parse(sourceHtml);
		return doc.title();
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

	private @NotNull Map<String, LemmaEntity> removeMostFrequentlyLemmas(@NotNull Map<String, LemmaEntity> source, float thresholdFreq) {
		Map<String, LemmaEntity> result = source.entrySet()
				.stream()
				.filter(entry -> entry.getValue().getFrequency() <= thresholdFreq)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		System.out.println("Remained lemmas are:");
		result.keySet().forEach(r -> System.out.println(" ".repeat(22) + r));
		System.out.println("Working set of lemmas contains " + result.size() + " lemma(s) after deletion of most frequently");
		return result;
	}
}
