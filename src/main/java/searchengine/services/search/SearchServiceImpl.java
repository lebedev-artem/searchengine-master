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
	List<SiteEntity> siteEntities = new ArrayList<>();
	Map<String, LemmaEntity> retrievedLemmas = new TreeMap<>();
	Map<String, LemmaEntity> finalLemmas = new TreeMap<>();
	Map<Integer, PageEntity> resultPages = new HashMap<>();
	Map<PageEntity, Map<String, LemmaEntity>> pagesLemma = new HashMap<>();

	@Override
	public SearchResponse getSearchResults(@NotNull String query, String siteUrl, Integer offset, Integer limit) {
		SearchResponse response = new SearchResponse();
		List<SearchData> fullData = new ArrayList<>();
		Set<PageEntity> pageEntities = new HashSet<>();
		Set<IndexEntity> indexEntities = new HashSet<>();

		if (query.isEmpty()) return emptyQuery();

		//получаем леммы из запроса
		Set<String> queryLemmas = lemmaFinder.getLemmaSet(query);


		if (siteUrl != null) {
			siteEntities.add(siteRepository.findByUrl(siteUrl));
		} else {
			siteEntities = siteRepository.findAll();
		}

		for (SiteEntity site : siteEntities) {
			//получаем леммы из базы по сайту
			retrievedLemmas = getLemmaEntitiesFromTable(queryLemmas, site);
			if (retrievedLemmas.size() == 0) continue;

			//Считаем суммарную частоту
			int totalFreq = getTotalFrequency(retrievedLemmas);
			float thresholdFreq = (float) ((totalFreq / retrievedLemmas.size()) * ratio);

			//удаляем слишком частые
			retrievedLemmas = removeMostFrequentlyLemmas(retrievedLemmas, thresholdFreq);
			retrievedLemmas = sortByFrequency(retrievedLemmas);


			//Получаем индекс по самой редкой лемме, и дальше работаем с ним
			String rarestLemma = retrievedLemmas.keySet().stream().findFirst().orElse(null);
			Set<IndexEntity> rarestIndex = getRarestIndex(site, rarestLemma);
			if (rarestIndex == null) continue;

			//Извлекаем страницы из базы по самым редким индексам и работаем с ними
			Map<Integer, PageEntity> rarestPages = getPageEntitiesByIndex(site, rarestIndex);

			//------------------------------------cut---------------------------------------
			System.out.println("Rarest lemma - " + rarestLemma + " freq - " + lemmaRepository.getByLemmaAndSiteEntity(rarestLemma, site).getFrequency());
			System.out.println("pages by rarest lemma");
			rarestPages.keySet().forEach(k -> {
				System.out.println(k + " " + rarestPages.get(k).getPath());
			});
			System.out.println("-----------------------------------------------------------");
			//---------------------------------end---cut------------------------------------


			//Продолжаем искать оставшиеся леммы на этих страницах
			finalLemmas = getFinalLemmas(rarestLemma, retrievedLemmas);


			Map<Integer, PageEntity> finalPages = new HashMap<>(rarestPages);
			for (String key : finalLemmas.keySet()) {

				LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteEntity(key, site);
				Set<IndexEntity> indexSet = indexRepository.findAllByLemmaEntity(lemmaEntity);

				//создаем сет <PageId, Index>
				Map<Integer, IndexEntity> pageIdIndex = createPageIdIndexMap(indexSet);

				//------------------------------------cut---------------------------------------
				System.out.println("lemma " + key + " freq - " + lemmaRepository.getByLemmaAndSiteEntity(key, site).getFrequency());
				indexSet.forEach(k -> {
					System.out.println(k.getPageEntity().getId() + " " + k.getPageEntity().getPath());
				});
				System.out.println("-----------------------------------------------------------");
				//---------------------------------end---cut------------------------------------

				//ищем вхождение очередной леммы в сет по самой редкой лемме
				final Map<Integer, PageEntity> finalWorkingPages = finalPages;
				Map<Integer, PageEntity> eachIterationPages = new HashMap<>();
				pageIdIndex.keySet().forEach(x -> {
					if (finalWorkingPages.containsKey(x)) {
						eachIterationPages.put(x, rarestPages.get(x));
					}
				});
				finalPages.clear();
				finalPages.putAll(eachIterationPages);
			}

			resultPages.putAll(finalPages);

			//------------------------------------cut---------------------------------------
			System.out.println("results pages");
			resultPages.keySet().forEach(x -> {
				System.out.println(x + " " + resultPages.get(x).getPath());
			});
			System.out.println("-----------------------------------------------------------");
			//---------------------------------end---cut------------------------------------

			Map<PageEntity, Float> pagesAbsoluteMap = calculateAbsRel(resultPages, retrievedLemmas);
			float maxAbsRel = getMaxRank(pagesAbsoluteMap);
			Map<PageEntity, Float> pagesRelativeMap = calculateRelevance(pagesAbsoluteMap, maxAbsRel);

			//сортируем по Rank
			pagesRelativeMap = sortByRank(pagesRelativeMap);

			List<SearchData> siteData = new ArrayList<>();
			Map<PageEntity, Float> resultSortedPagesSet = pagesRelativeMap;
			response = new SearchResponse();
			fullData = new ArrayList<>();

			for (PageEntity page : resultSortedPagesSet.keySet()) {
				SearchData searchData = new SearchData();
				searchData.setSiteName(site.getName());
				searchData.setSite(site.getUrl().substring(0, site.getUrl().lastIndexOf("/")));
				searchData.setUri(page.getPath());
				searchData.setSnippet("snippet");
				searchData.setRelevance(resultSortedPagesSet.get(page));
				searchData.setTitle(getTitle(page.getContent()));
				siteData.add(searchData);
			}

			fullData.addAll(siteData);
			System.out.printf("asd");
		}

		response.setData(fullData);
		response.setResult(true);
		response.setCount(fullData.size());

		clearAllGlobalVariable();

		return response;
	}

	private String getTitle(String sourceHtml) {
		Document doc = Jsoup.parse(sourceHtml);
		return doc.title();
	}

	private void clearAllGlobalVariable() {
		siteEntities.clear();
		retrievedLemmas.clear();
		finalLemmas.clear();
		resultPages.clear();
	}

	private float getMaxRank(@NotNull Map<PageEntity, Float> source) {
		Float maxValue = null;
		for (Float value : source.values()) {
			if (maxValue == null || value > maxValue) {
				maxValue = value;
			}
		}
		return maxValue;
	}

	private Map<PageEntity, Float> calculateRelevance(Map<PageEntity, Float> source, float maxAbsRel) {
		Map<PageEntity, Float> result = new HashMap<>();
		for (PageEntity p : source.keySet()) {
			result.put(p, source.get(p) / maxAbsRel);
		}
		return result;
	}

	private @NotNull Map<PageEntity, Float> calculateAbsRel(Map<Integer, PageEntity> source, Map<String, LemmaEntity> lemmas) {
		Map<PageEntity, Float> result = new HashMap<>();
		for (Integer pId : source.keySet()) {
			Map<String, LemmaEntity> value = new HashMap<>();
			float absRelPage = (float) 0;
			PageEntity p = source.get(pId);
			for (LemmaEntity l : lemmas.values()) {
				IndexEntity idx = indexRepository.findByLemmaEntityAndPageEntity(l, p);
				if (idx != null) {
					absRelPage += idx.getLemmaRank();
					value.put(idx.getLemmaEntity().getLemma(), idx.getLemmaEntity());
				}
			}
			pagesLemma.put(p, value);
			result.put(p, absRelPage);
		}
		return result;
	}

	private @NotNull Map<String, LemmaEntity> getFinalLemmas(String rarestLemma, Map<String, LemmaEntity> source) {
		Map<String, LemmaEntity> result = new HashMap<>(source);
		result.remove(rarestLemma);
		return sortByFrequency(result);
	}

	private Map<Integer, IndexEntity> createPageIdIndexMap(Set<IndexEntity> indexSet) {
		Map<Integer, IndexEntity> result = new HashMap<>();
		indexSet.forEach(x -> result.put(x.getId().getPageId(), x));
		return result;
	}

	private @Nullable Set<IndexEntity> getRarestIndex(SiteEntity site, String rarestLemma) {
		LemmaEntity rarestLemmaEntity = lemmaRepository.findByLemmaAndSiteEntity(rarestLemma, site);
		if (rarestLemmaEntity != null)
			return indexRepository.findAllByLemmaEntity(rarestLemmaEntity);
		return null;
	}

	@NotNull
	private static SearchResponse emptyQuery() {
		return new SearchResponse(false, "Задан пустой поисковый запрос");
	}

	private Map<Integer, PageEntity> getPageEntitiesByIndex(SiteEntity site, Set<IndexEntity> indexEntities) {
		Map<Integer, PageEntity> result = new HashMap<>();
		for (IndexEntity obj : indexEntities) {
			int pageId = obj.getPageEntity().getId();
			PageEntity p = pageRepository.findByIdAndSiteEntity(pageId, site);
			if (p != null) result.put(p.getId(), p);
		}
		return result;
	}

	private Set<IndexEntity> getIndexEntitiesByLemma(Map<String, LemmaEntity> sortedLemmasMap, String key) {
		Set<IndexEntity> indexByLemma;
		indexByLemma = indexRepository.findAllByLemmaEntity(sortedLemmasMap.get(key));
		return indexByLemma;
	}

	private Map<String, LemmaEntity> removeMostFrequentlyLemmas(Map<String, LemmaEntity> source, float thresholdFreq) {
		for (String key : source.keySet()) {
			if (source.get(key).getFrequency() > thresholdFreq) source.remove(key);
		}
		return new TreeMap<>(source);
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
