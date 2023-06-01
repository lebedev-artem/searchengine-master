package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.IndexingStatus;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.StatisticsService;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

	private final SitesList sites;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;

	@Override
	public StatisticsResponse getStatistics() {
		log.warn("Mapping /statistics executed");
		int totalPages = 0;
		int totalLemmas = 0;

		List<DetailedStatisticsItem> detailed = new ArrayList<>();

		List<Site> sitesList = sites.getSites();
		for (Site site : sitesList) {
			SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
			if (siteEntity != null) {
				int pages = pageRepository.countBySiteEntity(siteEntity);
				int lemmas = lemmaRepository.countBySiteEntity(siteEntity);
				totalPages += pages;
				totalLemmas += lemmas;

				DetailedStatisticsItem item = getDetailedStatisticsItem(site, pages, lemmas, siteEntity);
				detailed.add(item);
			}
		}

		TotalStatistics total = getTotalStatistics(totalPages, totalLemmas);
		StatisticsData data = getStatisticsData(detailed, total);

		return getResponse(data);
	}

	private static StatisticsResponse getResponse(StatisticsData data) {
		return StatisticsResponse.builder()
				.statistics(data)
				.result(true).build();
	}

	private static StatisticsData getStatisticsData(List<DetailedStatisticsItem> detailed, TotalStatistics total) {
		return StatisticsData.builder()
				.total(total)
				.detailed(detailed).build();
	}

	private TotalStatistics getTotalStatistics(int totalPages, int totalLemmas) {
		return TotalStatistics.builder()
				.sites(sites.getSites().size())
				.indexing(getIsIndexingStarted())
				.lemmas(totalLemmas)
				.pages(totalPages).build();
	}

	private @NotNull DetailedStatisticsItem getDetailedStatisticsItem(@NotNull Site site, int pages, int lemmas, @NotNull SiteEntity siteEntity) {

		return DetailedStatisticsItem.builder()
				.name(site.getName())
				.url(site.getUrl())
				.pages(pages)
				.lemmas(lemmas)
				.status(siteEntity.getStatus().toString())
				.error(siteEntity.getLastError())
				.statusTime(Date.from(siteEntity.getStatusTime().atZone(ZoneId.systemDefault()).toInstant())).build();
	}

	private Boolean getIsIndexingStarted() {
		List<SiteEntity> sites = siteRepository.findAll();

		for (SiteEntity s : sites) {
			if (s.getStatus() == IndexingStatus.INDEXING) {
				return true;
			}
		}
		return false;
	}
}
