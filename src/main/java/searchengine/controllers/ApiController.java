package searchengine.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.SiteEntity;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexService;
import searchengine.services.indexing.IndexServiceImpl;
import searchengine.services.indexing.Task;
import searchengine.services.StatisticsService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    @Autowired
    private final SitesList sitesList;
    public static final Logger LOGGER = LogManager.getLogger(ApiController.class);
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private IndexService indexService;

    public ApiController(StatisticsService statisticsService, SitesList sitesList, IndexService indexService) {
        this.statisticsService = statisticsService;
        this.sitesList = sitesList;
        this.indexService = indexService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public String startIndexing(){
        LOGGER.info("@GetMapping (\"/startIndexing) running");
        List<Site> sites = sitesList.getSites();
        for (Site s : sites){
            indexService.indexingStart(s);
        }
        return "Saved";
    }
}
