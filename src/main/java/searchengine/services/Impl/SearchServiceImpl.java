package searchengine.services.Impl;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.data.domain.*;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

	public static final double ratio = 1.80;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final IndexRepository indexRepository;
	private final LemmaFinder lemmaFinder;
	private final SnippetGenerator snippetGenerator;

	private String rarestLemma = null;
	private Map<String, LemmaEntity> retrievedLemmas = new HashMap<>();
	private Map<String, LemmaEntity> nextStepLemmas = new HashMap<>();
	private List<PageEntity> currentPageEntities = new ArrayList<>();
	private Map<PageEntity, Float> totalPagesWithRelevance = new HashMap<>();
	private List<PageEntity> totalPages = new ArrayList<>();

	@Override
	@Builder
	public SearchResponse getSearchResults(@NotNull String query, String siteUrl, Integer offset, Integer limit) {
		if (query.isEmpty()) return emptyQuery();

		int totalPagesCount = 0;

		//Get lemmas from search query
		Set<String> queryLemmas = lemmaFinder.getLemmaSet(query);
		List<String> queryList = new ArrayList<>(queryLemmas);

		//One or all sites
		List<SiteEntity> siteEntities = siteUrl != null
				? Collections.singletonList(siteRepository.findByUrl(siteUrl))
				: siteRepository.findAll();

		for (SiteEntity site : siteEntities) {
			//Get lemmas from DB by Site
			retrievedLemmas = getLemmaEntitiesFromTable(queryLemmas, site);
			if (retrievedLemmas.size() != 0) {

				//Calculate threshold of frequency for deleting
				int totalFreq = getTotalFrequency(retrievedLemmas);
				float thresholdFreq = (float) ((totalFreq / retrievedLemmas.size()) * ratio);

				//Delete most frequently lemmas
				nextStepLemmas = removeMostFrequentlyLemmas(retrievedLemmas, thresholdFreq);
				//Sort by frequency
				nextStepLemmas = sortByFrequency(nextStepLemmas);

				//Get list of Pages by rarest lemma
				rarestLemma = nextStepLemmas.keySet().stream().findFirst().orElse(null);
				if (rarestLemma != null) {
					List<Integer> pageIds = getPageEntitiesIdsByRarestLemma(site);

					if (siteEntities.size() == 1) {
						List<PageEntity> pagesList = pageRepository.findAllByIdIn(pageIds);
//						List<PageEntity> subPages = pagesList.stream()
//								.skip(offset)
//								.limit(limit)
//								.toList();
						Pageable pageable = PageRequest.of(offset, limit);
						Page<PageEntity> pageableObject = pageRepository.findAllByIdIn(pageIds, pageable);
//						List<PageEntity> onlyLimitPages = pageableObject.getContent();

//						Page<PageEntity> currentPageObj = new PageImpl<>(subPages, PageRequest.of(offset, limit), totalPages);
						currentPageEntities = pageableObject.getContent();
						totalPagesCount = totalPagesCount + currentPageEntities.size();
					} else {
						currentPageEntities = pageRepository.findAllByIdIn(pageIds);
						totalPagesCount = totalPagesCount + currentPageEntities.size();
					}
				}

//				printRarestLemma(site, rarestLemma, new HashSet<>(currentPageEntities));
			}

			//We continue to look for the remaining lemmas on these pages
			Map<String, LemmaEntity> finalLemmas = getFinalSortedLemmasMap(rarestLemma, nextStepLemmas);

			List<PageEntity> finalPages = new ArrayList<>(currentPageEntities);
			for (String desiredLemma : finalLemmas.keySet()) {
				List<PageEntity> eachIterationPages = getRetainedPages(site, currentPageEntities, desiredLemma);
				finalPages.clear();
				finalPages.addAll(eachIterationPages);
				totalPagesCount = totalPagesCount + finalPages.size();
			}

			//Calculate absolute relevancy
			Map<PageEntity, Float> pagesWithAbsRelativeMap = calculateAbsRelevance(finalPages, nextStepLemmas);
			totalPagesWithRelevance.putAll(pagesWithAbsRelativeMap);
		}

		System.out.println("before calculate relevance");
		for (PageEntity pp:totalPagesWithRelevance.keySet()) {
			System.out.println(pp.getId() + " " + getTitle(pp.getContent()) + " absolute relevance = " + totalPagesWithRelevance.get(pp));
		}
		totalPagesWithRelevance = calculateRelevance(totalPagesWithRelevance, getMaxRank(totalPagesWithRelevance));
		System.out.println("after calculate relevance");
		for (PageEntity pp:totalPagesWithRelevance.keySet()) {
			System.out.println(pp.getId() + " " + getTitle(pp.getContent()) + " absolute relevance = " + totalPagesWithRelevance.get(pp));
		}

		totalPagesWithRelevance = sortByRank(totalPagesWithRelevance);
		System.out.println("after sort");
		for (PageEntity pp:totalPagesWithRelevance.keySet()) {
			System.out.println(pp.getId() + " " + getTitle(pp.getContent()) + " absolute relevance = " + totalPagesWithRelevance.get(pp));
		}

		List<SearchData> totalData = new ArrayList<>();

		Map<PageEntity, Float> subsetMap = new HashMap<>();
		if (siteEntities.size() == 1) {
			subsetMap = new HashMap<>(totalPagesWithRelevance);
			subsetMap = sortByRank(subsetMap);
		} else if (siteEntities.size() > 1) {
			subsetMap = getSortedSubsetMap(totalPagesWithRelevance, limit, offset);
			totalPagesCount = totalPagesWithRelevance.size();
		}
		totalPagesWithRelevance.clear();

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
		if (totalPagesCount == 0) {
			totalData = new ArrayList<>();
		}
		if (totalData.size() == 0) {
			totalPagesCount = 0;
		}
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
		// Сортируем Map в порядке убывания значений Float
		List<Map.Entry<PageEntity, Float>> sortedEntries = new ArrayList<>(source.entrySet());
		sortedEntries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

		// Выбираем подмножество элементов
		int endIndex = Math.min(offset + limit, source.size());
		List<Map.Entry<PageEntity, Float>> subsetEntries = sortedEntries.subList(offset, endIndex);

		// Создаем новую Map с выбранными элементами
		Map<PageEntity, Float> result = new LinkedHashMap<>();
		for (Map.Entry<PageEntity, Float> entry : subsetEntries) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	private @NotNull Map<String, LemmaEntity> getLemmaEntitiesFromTable(@NotNull Set<String> queryLemmas, SiteEntity site) {
		return queryLemmas.stream()
				.map(lemma -> lemmaRepository.findByLemmaAndSiteEntity(lemma, site))
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
		list.sort((o1, o2) -> o1.getValue().getFrequency() - o2.getValue().getFrequency());
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

	private Map<PageEntity, Float> getPagesWithRelevance(@NotNull List<PageEntity> source) {
		if (source.size() != 0) {
			Map<PageEntity, Float> pagesAbsoluteMap = calculateAbsRelevance(source, retrievedLemmas);
			float maxAbsRel = getMaxRank(pagesAbsoluteMap);
			return calculateRelevance(pagesAbsoluteMap, maxAbsRel);
		}
		return new HashMap<>();
	}

	private @NotNull Map<PageEntity, Float> calculateAbsRelevance(@NotNull List<PageEntity> source, Map<String, LemmaEntity> lemmas) {
		Map<PageEntity, Float> result = new HashMap<>();
		for (PageEntity p : source) {
			System.out.println("page " + p.getId() + " " + getTitle(p.getContent()));
			float absRelPage = (float) 0;
			for (LemmaEntity l : lemmas.values()) {
				IndexEntity idx = indexRepository.findByLemmaEntityAndPageEntity(l, p);
				if (idx != null) {
					System.out.println("lemma " + l.getLemma() + " " + l.getId() + " exists");
					absRelPage += idx.getLemmaRank();
				}
			}
			result.put(p, absRelPage);
			System.out.println("page " + p.getId() + " " + getTitle(p.getContent()) + " has absolute rel = " + absRelPage);
		}
		return result;
	}

	private float getMaxRank(@NotNull Map<PageEntity, Float> source) {
		Float maxValue = source.values().stream().max(Comparator.naturalOrder()).orElse(null);
		return maxValue == null ? 1 : maxValue;
	}

	private Map<PageEntity, Float> calculateRelevance(@NotNull Map<PageEntity, Float> source, float maxAbsRel) {
		System.out.println("Max relevance = " + maxAbsRel);
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


//	private void printResultPages(String results_pages, Set<PageEntity> resultPages) {
//		System.out.println(results_pages);
//		resultPages.forEach(x -> {
//			System.out.println(x + " " + x.getPath());
//		});
//		System.out.println("-----------------------------------------------------------");
//	}

//	private void printRarestLemma(SiteEntity site, String rarestLemma, Set<PageEntity> rarestPages) {
//		if (rarestLemma != null && rarestPages != null) {
//			System.out.println("Rarest lemma - " + rarestLemma + " freq - " + lemmaRepository.getByLemmaAndSiteEntity(rarestLemma, site).getFrequency());
//			printResultPages("pages by rarest lemma", rarestPages);
//		}
//	}

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
		return source.entrySet()
				.stream()
				.filter(entry -> entry.getValue().getFrequency() <= thresholdFreq)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}
