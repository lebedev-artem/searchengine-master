package searchengine.services.Impl;

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
import searchengine.model.SiteEntity;
import searchengine.services.RepositoryService;
import searchengine.services.StatisticsService;
import searchengine.tools.indexing.IndexingActions;

import java.util.ArrayList;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final RepositoryService repositoryService;
    private final IndexingActions indexingActions;

    @Override
    public StatisticsResponse getStatistics() {
        log.warn("Mapping /statistics executed");

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(indexingActions.isIndexingActionsStarted());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            SiteEntity siteEntity = repositoryService.getSiteByUrl(site.getUrl());

            if (siteEntity != null) {
                int pages = repositoryService.countPagesFromSite(siteEntity);
                int lemmas = repositoryService.countLemmasFromSite(siteEntity);
                DetailedStatisticsItem item = setDetailedStatisticsItem(site, pages, lemmas, siteEntity);

                total.setPages(total.getPages() + pages);
                total.setLemmas(total.getLemmas() + lemmas);
                detailed.add(item);
            }
        }

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private @NotNull DetailedStatisticsItem setDetailedStatisticsItem(Site site, int pages, int lemmas, SiteEntity siteEntity){
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        item.setPages(pages);
        item.setLemmas(lemmas);
        item.setStatus(siteEntity.getStatus().toString());
        item.setError(siteEntity.getLastError());
        item.setStatusTime(siteEntity.getStatusTime());
        return item;
    }
}
