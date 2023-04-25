package searchengine.services.Impl;

import lombok.Builder;
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
import searchengine.services.RepositoryService;
import searchengine.services.SearchService;
import searchengine.tools.LemmaFinder;
import searchengine.tools.SnippetGenerator;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

	public static final double RATIO = 1.85;
	private String rarestLemma = null;
	private final LemmaFinder lemmaFinder;
	private final SnippetGenerator snippetGenerator;
	private final RepositoryService repositoryService;

	private List<PageEntity> pagesByRarestLemma = new ArrayList<>();
	private Map<String, LemmaEntity> nextStepLemmas = new HashMap<>();
	private Map<PageEntity, Float> totalPagesWithRelevance = new HashMap<>();

	@Override
	@Builder
	public SearchResponse getSearchResults(@NotNull String query, String siteUrl, Integer offset, Integer limit) {
		if (query.isEmpty()) return emptyQuery();

		//Get lemmas from search query
		Set<String> queryLemmas = lemmaFinder.getLemmaSet(query);
		List<String> queryList = new ArrayList<>(queryLemmas);

		//One or all sites
		List<SiteEntity> siteEntities = siteUrl != null
				? Collections.singletonList(repositoryService.getSiteByUrl(siteUrl))
				: repositoryService.getSites();

		int totalPagesCount = 0;

		for (SiteEntity site : siteEntities) {
			Map<String, LemmaEntity> retrievedLemmas = getLemmaEntitiesFromTable(queryLemmas, site);
			if (retrievedLemmas.size() != 0) {

				//Calculate threshold of frequency for deleting
				int totalFreq = getTotalFrequency(retrievedLemmas);
				float thresholdFreq = (float) ((totalFreq / retrievedLemmas.size()) * RATIO);

				//Delete most frequently lemmas, Sort by frequency
				nextStepLemmas = removeMostFrequentlyLemmas(retrievedLemmas, thresholdFreq);
				nextStepLemmas = sortByFrequency(nextStepLemmas);

				//Get list of Pages by rarest lemma
				rarestLemma = nextStepLemmas.keySet().stream().findFirst().orElse(null);
				if (rarestLemma != null) {
					List<Integer> pageIds = getPageEntitiesIdsByRarestLemma(site);

					pagesByRarestLemma = repositoryService.getPagesByIds(pageIds);
					totalPagesCount += pagesByRarestLemma.size();
				}
			}

			//We continue to look for the remaining lemmas on these pages
			Map<String, LemmaEntity> finalLemmas = getFinalSortedLemmasMap(rarestLemma, nextStepLemmas);

			List<PageEntity> finalPages = new ArrayList<>(pagesByRarestLemma);
			for (String desiredLemma : finalLemmas.keySet()) {
				List<PageEntity> eachIterationPages = getRetainedPages(site, pagesByRarestLemma, desiredLemma);
				finalPages.clear();
				finalPages.addAll(eachIterationPages);
			}

			//Calculate absolute relevancy
			Map<PageEntity, Float> pagesWithAbsRelativeMap = calculateAbsRelevance(finalPages, nextStepLemmas);
			totalPagesWithRelevance.putAll(pagesWithAbsRelativeMap);
		}

		float maxRank = getMaxRank(totalPagesWithRelevance);
		totalPagesWithRelevance = calculateRelevance(totalPagesWithRelevance, maxRank);
		totalPagesWithRelevance = sortByRank(totalPagesWithRelevance);

		//Consider limit offset
		Map<PageEntity, Float> subsetMap = getSortedSubsetMap(totalPagesWithRelevance, limit, offset);

		List<SearchData> totalData = new ArrayList<>();

		for (PageEntity page : subsetMap.keySet()) {

			SearchData searchData = SearchData.builder()
					.uri(page.getPath())
					.siteName(page.getSiteEntity().getName())
					.site(page.getSiteEntity().getUrl().replaceFirst("/$", ""))
					.snippet(getSnippet(page, queryList))
					.relevance(subsetMap.get(page))
					.title(getTitle(page.getContent())).build();

			totalData.add(searchData);
		}

		totalPagesWithRelevance.clear();

		return SearchResponse.builder()
				.result(true)
				.count(totalPagesCount)
				.data(totalData)
				.build();
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
		for (Map.Entry<PageEntity, Float> entry : subsetEntries) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	private @NotNull Map<String, LemmaEntity> getLemmaEntitiesFromTable(@NotNull Set<String> queryLemmas, SiteEntity site) {
		return queryLemmas.stream()
				.map(lemma -> repositoryService.getLemmaByNameFromSite(lemma, site))
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(LemmaEntity::getLemma, Function.identity()));
	}

	private @NotNull Integer getTotalFrequency(Map<String, LemmaEntity> lemmaEntities) {
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
		LemmaEntity lemmaEntity = repositoryService.getLemmaByNameFromSite(lemma, site);
		Set<IndexEntity> indexSet = repositoryService.getAllByLemma(lemmaEntity);
		return finalPages.stream()
				.filter(pageEntity -> indexSet.stream()
						.anyMatch(index -> Objects.equals(index.getPageEntity().getId(), pageEntity.getId())))
				.toList();
	}

	private @NotNull Map<PageEntity, Float> calculateAbsRelevance(@NotNull List<PageEntity> source, Map<String, LemmaEntity> lemmas) {
		Map<PageEntity, Float> result = new HashMap<>();
		for (PageEntity p : source) {
			float absRelPage = (float) 0;
			for (LemmaEntity l : lemmas.values()) {
				IndexEntity idx = repositoryService.getIndexByLemmaFromPage(l, p);
				if (idx != null) {
					absRelPage += idx.getLemmaRank();
				}
			}
			result.put(p, absRelPage);
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
		LemmaEntity rarestLemmaEntity = repositoryService.getLemmaByNameFromSite(rarestLemma, site);
		if (rarestLemmaEntity != null)
			return repositoryService.getAllByLemma(rarestLemmaEntity);

		return null;
	}

	@NotNull
	private static SearchResponse emptyQuery() {
		return new SearchResponse(false, "Задан пустой поисковый запрос", 0, new ArrayList<>());
	}

	private @NotNull Map<String, LemmaEntity> removeMostFrequentlyLemmas(@NotNull Map<String, LemmaEntity> source, float thresholdFreq) {
		return source.entrySet()
				.stream()
				.filter(entry -> entry.getValue().getFrequency() <= thresholdFreq)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}
