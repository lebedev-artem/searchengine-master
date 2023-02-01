package searchengine.controllers;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jsoup.internal.NonnullByDefault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.BaseRepository;
import searchengine.services.indexing.IndexResponse;
import searchengine.services.interfaces.IndexService;
import searchengine.services.interfaces.StatisticsService;
import searchengine.services.utilities.FillEntityImpl;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api")
public class ApiController {

//	@Autowired
//	private final PageEntityRepository pageEntityRepository;
	private final StatisticsService statisticsService;
	@Autowired
	private final SitesList sitesList;
//	@Autowired
//	private final SiteEntityRepository siteEntityRepository;
	private static final Logger logger = LogManager.getLogger(ApiController.class);
	@Autowired
	private final IndexService indexService;
	@Autowired
	private FillEntityImpl fillEntityImpl;
	TotalStatistics totalStatistics = new TotalStatistics();
	private final IndexResponse indexResponse = new IndexResponse();
	private volatile boolean isStarted = false;


	public ApiController(StatisticsService statisticsService, SitesList sitesList, IndexService indexService) {
		this.statisticsService = statisticsService;
		this.sitesList = sitesList;
		this.indexService = indexService;
	}

//	public ApiController() {
//	}

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
		if (!isStarted) return indexResponse.stopFailed();
		isStarted = false;
		return indexService.indexingStop();
	}

	@PostMapping("/indexPage")
	public ResponseEntity<?> indexPage(HttpServletRequest request) {
		boolean urlNotPresent = true;
		for (Site sL : sitesList.getSites())
			if (request.getParameter("url").equals(sL.getUrl())) urlNotPresent = false;
		if (urlNotPresent) return indexResponse.indexPageFailed();
		return indexResponse.successfully();
	}
}
