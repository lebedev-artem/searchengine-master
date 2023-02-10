package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexResponse;
import searchengine.services.interfaces.IndexService;
import searchengine.services.interfaces.StatisticsService;

import javax.servlet.http.HttpServletRequest;
import java.net.URL;
import java.util.concurrent.*;

@Setter
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

	@Autowired
	private final PageRepository pageRepository;
	@Autowired
	private final SiteRepository siteRepository;
	@Autowired
	private final LemmaRepository lemmaRepository;
	@Autowired
	private final SearchIndexRepository searchIndexRepository;
	@Autowired
	private final IndexService indexService;
	@Autowired
	private final SitesList sitesList;

	private static final Logger logger = LogManager.getLogger(ApiController.class);
	private final StatisticsService statisticsService;
	TotalStatistics totalStatistics = new TotalStatistics();
	private static final IndexResponse indexResponse = new IndexResponse();

	@GetMapping("/statistics")
	public ResponseEntity<StatisticsResponse> statistics() {
		return ResponseEntity.ok(statisticsService.getStatistics());
	}

	@GetMapping("/startIndexing")
	public ResponseEntity<?> startIndexing() throws Exception {
		return indexService.indexingStart(sitesList);
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<?> stopIndexing() throws ExecutionException, InterruptedException {
		return indexService.indexingStop();
	}

	@PostMapping("/indexPage")
	public ResponseEntity<?> indexPage(@NotNull HttpServletRequest request) throws Exception {
		return indexService.singleIndexingStart(request);
	}
}
