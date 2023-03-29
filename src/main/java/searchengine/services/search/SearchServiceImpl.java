package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import searchengine.services.lemmatization.LemmasAndIndexCollectingService;

import java.util.*;

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

	@Override
	public SearchResponse getSearchResults(String query, String siteUrl, Integer offset, Integer limit) {
		SearchResponse response = new SearchResponse();
		List<SearchData> data = new ArrayList<>();
		SearchData dataItem = new SearchData();
		Set<PageEntity> pageEntities = new HashSet<>();
		Set<IndexEntity> indexEntities = new HashSet<>();


		if (query.isEmpty()) {
			response.setError("Задан пустой поисковый запрос");
			response.setResult(false);
			return response;
		}

		//получаем леммы из запроса
		Set<String> queryLemmas = lemmaFinder.getLemmaSet(query);
		//получаем список сайтов
		List<SiteEntity> siteEntities = siteRepository.findAll();


		for (SiteEntity site : siteEntities) {
			//получаем леммы из базы по сайту
			Map<String, LemmaEntity> lemmaEntities = getLemmaEntitiesFromTable(queryLemmas, site);

			//Создаем сортированный список лемм
			Map<String, LemmaEntity> sortedLemmasMap = getSortedMap(lemmaEntities);

			//Считаем суммарную частоту
			int totalFreq = getTotalFrequency(lemmaEntities);
			float thresholdFreq = (float) ((totalFreq / sortedLemmasMap.size()) * ratio);

			//удаляем слишком частые
			removeMostFrequentlyLemmas(sortedLemmasMap, thresholdFreq);

			//Получаем индекс по самой редкой лемме, и дальше работаем с ним
			Set<IndexEntity> indexRarestLemma = new HashSet<>();
			String rarestLemma = sortedLemmasMap.keySet().stream().findFirst().orElse(null);

			if (rarestLemma != null)
				indexRarestLemma = indexRepository.findAllByLemmaEntity(lemmaRepository.findByLemmaAndSiteEntity(rarestLemma, site));

			if (indexRarestLemma == null) continue;

			//Извлекаем страницы из базы по самым редким индексам и работаем с ними
			Map<Integer, PageEntity> pagesByRarestLemma = getPageEntitiesByIndex(site, indexRarestLemma);

			//добавляем все в итоговые сеты
			pageEntities.addAll(pagesByRarestLemma.values());
			indexEntities.addAll(indexRarestLemma);

			//Продолжаем искать оставшиеся леммы на этих страницах
			Map<String, LemmaEntity> remainingLemmas = new TreeMap<>(sortedLemmasMap);
			remainingLemmas.remove(rarestLemma);


			for (String key : remainingLemmas.keySet()) {
				LemmaEntity lemmaEntity  = lemmaRepository.findByLemmaAndSiteEntity(key, site);
				Set<IndexEntity> indexSet = indexRepository.findAllByLemmaEntity(lemmaEntity);
				System.out.printf(String.valueOf(indexSet.size()));
			}








			Map<PageEntity, Float> pagesAbsoluteMap = new HashMap<>();
			Map<PageEntity, Float> pagesRelativeMap = new HashMap<>();
			//рассчитываем релевантность абсолютную
			float maxAbsRel = (float) 0;
			for (IndexEntity idx : indexRarestLemma) {
				Integer pageId = idx.getId().getPageId();
				Float rank = idx.getLemmaRank();
				PageEntity pageEntity = pagesByRarestLemma.get(pageId);
				if (!pagesByRarestLemma.containsKey(pageId)) continue;

				Float oldRel;
				if (pagesAbsoluteMap.containsKey(pageId)) {
					rank += pagesAbsoluteMap.get(pageEntity);
				}
				pagesAbsoluteMap.put(pageEntity, rank);

				if (rank > maxAbsRel)
					maxAbsRel = rank;
			}

			//рассчитываем относительную релевантность
			for (PageEntity obj:pagesAbsoluteMap.keySet()) {
				pagesRelativeMap.put(obj, pagesAbsoluteMap.get(obj) / maxAbsRel);
			}


			System.out.printf("asd");
		}

		dataItem.setSiteName(siteRepository.findByUrl(siteUrl).getName());
		dataItem.setSite(siteUrl);
		dataItem.setUri("uri");
		dataItem.setSnippet("snippet");
		data.add(dataItem);
		response.setData(data);
		response.setResult(true);
		response.setCount(100);
		//Убираем слишком часто встречающиеся

		//сортируем сет лемм

		//исключаем слишком частые

		return response;
	}

	private Map<Integer, PageEntity> getPageEntitiesByIndex(SiteEntity site, Set<IndexEntity> indexByLemma) {
		Map<Integer, PageEntity> result = new HashMap<>();
		for (IndexEntity obj : indexByLemma) {
			PageEntity p = pageRepository.findByIdAndSiteEntity(obj.getPageEntity().getId(), site);
			if (p != null) result.put(p.getId(), p);
		}
		return result;
	}

	private Set<IndexEntity> getIndexEntitiesByLemma(Map<String, LemmaEntity> sortedLemmasMap, String key) {
		Set<IndexEntity> indexByLemma;
		indexByLemma = indexRepository.findAllByLemmaEntity(sortedLemmasMap.get(key));
		return indexByLemma;
	}

	private static void removeMostFrequentlyLemmas(Map<String, LemmaEntity> sortedLemmasMap, float thresholdFreq) {
		for (String key : sortedLemmasMap.keySet()) {
			if (sortedLemmasMap.get(key).getFrequency() > thresholdFreq) sortedLemmasMap.remove(key);
		}
	}

	private Map<String, LemmaEntity> getSortedMap(Map<String, LemmaEntity> lemmaEntities) {
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

	private Map<String, LemmaEntity> getLemmaEntitiesFromTable(Set<String> queryLemmas, SiteEntity site) {
		Map<String, LemmaEntity> resultSet = new TreeMap<>();
		for (String lemma : queryLemmas) {
			LemmaEntity lemmaEntity = lemmaRepository.findFirstByLemmaAndSiteEntity(lemma, site);
			if (lemmaEntity == null) continue;
			resultSet.put(lemma, lemmaEntity);
		}
		return resultSet;
	}

	private Integer getTotalFrequency(Map<String, LemmaEntity> lemmaEntities) {
		int result = 0;
		for (String key : lemmaEntities.keySet()) {
			result += lemmaEntities.get(key).getFrequency();
		}
		return result;
	}
}
