package searchengine.controllers;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexService;
import searchengine.services.StatisticsService;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    @Autowired
    private final SitesList sitesList;
    private static final Logger logger =LogManager.getRootLogger();
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
        siteRepository.deleteAll();
        siteRepository.resetIndex();
        siteRepository.flush();
        logger.warn("@GetMapping (\"/startIndexing) running");
        List<Site> sites = sitesList.getSites();
        for (Site s : sites){
            siteRepository.deleteByName(s.getName());
            logger.info("Start indexing " + s.getName() + " " + s.getUrl());
            indexService.indexingStart(s);
            siteRepository.changeSiteStatus("INDEXED", s.getName());
        }
        System.out.printf("Finished");
        return "Saved";
    }
}
