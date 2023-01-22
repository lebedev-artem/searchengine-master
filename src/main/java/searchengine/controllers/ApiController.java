package searchengine.controllers;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.repositories.SiteRepository;
import searchengine.services.ShutdownService;
import searchengine.services.indexing.IndexResponse;
import searchengine.services.indexing.IndexService;
import searchengine.services.StatisticsService;
import searchengine.services.indexing.IndexServiceImpl;
import searchengine.services.utilities.FillEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api")
public class ApiController {

	private final StatisticsService statisticsService;
	@Autowired
	private final SitesList sitesList;
	private static final Logger logger = LogManager.getLogger(ApiController.class);
	@Autowired
	private SiteRepository siteRepository;
	@Autowired
	private IndexService indexService;
	@Autowired
	private FillEntity fillEntity;
	private IndexResponse indexResponse = new IndexResponse();
	private ExecutorService poolOfSites;
	private volatile boolean isStarted = false;
	List<Future<Boolean>> futures = new ArrayList<>();

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
	public ResponseEntity<?> startIndexing() {//        if we have to reset index to 0
		List<Site> sites = sitesList.getSites();
		poolOfSites = Executors.newFixedThreadPool(sites.size());
		if (isStarted) {
			return indexResponse.startFailed();
		}
		isStarted = true;
		for (Site s : sites) {
			futures.add(CompletableFuture.supplyAsync(() -> indexService.indexingStart(s), poolOfSites));
		}
		poolOfSites.shutdown();
		return indexResponse.successfully();
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<?> stopIndexing() throws InterruptedException {
		if (!isStarted) {
			logger.error("@GetMapping (\"/stopIndexing). Failed to stop.");
			return indexResponse.stopFailed();
		}
		indexService.indexingStop();
		ShutdownService service = new ShutdownService();
		service.stop(poolOfSites);

		isStarted = false;
		return indexResponse.successfully();
	}
}