package searchengine.controllers;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.repositories.CommonRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexResponse;
import searchengine.services.interfaces.IndexService;
import searchengine.services.interfaces.StatisticsService;
import searchengine.services.utilities.FillEntityImpl;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
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
	private FillEntityImpl fillEntityImpl;
	private IndexResponse indexResponse = new IndexResponse();
	private ForkJoinPool forkJoinPool = new ForkJoinPool();
	Thread t;
	private volatile boolean isStarted = false;


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
	public ResponseEntity<?> startIndexing() throws Exception {
		if (isStarted) return indexResponse.startFailed();
		isStarted = true;
		return indexService.indexingStart(sitesList);
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<?> stopIndexing() throws ExecutionException, InterruptedException {
		if (!isStarted) {
			logger.error("@GetMapping (\"/stopIndexing). Failed to stop.");
			return indexResponse.stopFailed();
		}
		indexService.indexingStop();
		isStarted = false;
		return indexResponse.successfully();
	}


	@PostMapping("/indexPage")
	public ResponseEntity<?> indexPage(HttpServletRequest request){
		boolean urlNotPresent = true;
		for (Site sL : sitesList.getSites())
			if (sL.getUrl().equals(request.getParameter("url"))) {
				urlNotPresent = false;
			}
		if (urlNotPresent) return indexResponse.indexPageFailed();
		return indexResponse.successfully();
	}
}
