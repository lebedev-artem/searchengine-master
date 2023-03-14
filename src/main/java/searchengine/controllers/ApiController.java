package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.Cache;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexResponse;
import searchengine.services.indexing.IndexingMode;
import searchengine.services.indexing.SchemaInitialization;
import searchengine.services.interfaces.IndexService;
import searchengine.services.interfaces.StatisticsService;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.concurrent.*;

@Setter
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {


	private static final Logger rootLogger = LogManager.getRootLogger();
	@Autowired
	IndexService indexService;
	@Autowired
	SitesList sitesList;
	@Autowired
	SchemaInitialization schemaInitialization;
	@Autowired
	SiteRepository siteRepository;
	@Autowired
	IndexResponse indexResponse;

	private final StatisticsService statisticsService;
	TotalStatistics totalStatistics = new TotalStatistics();

	@GetMapping("/statistics")
	public ResponseEntity<StatisticsResponse> statistics() {
		return ResponseEntity.ok(statisticsService.getStatistics());
	}

	@GetMapping("/startIndexing")
	public ResponseEntity<?> startIndexing() throws Exception {
		schemaInitialization.setMode(IndexingMode.FULL);
		Set<SiteEntity> siteEntities = schemaInitialization.fullInit();

		if (siteEntities.size() == 0) return indexResponse.startFailedEmptySites();

		for (SiteEntity s: siteEntities) {
			if (!siteRepository.existsByUrl(s.getUrl())){
				siteRepository.save(s);
			}
		}
		return indexService.indexingStart(siteEntities);
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<?> stopIndexing() throws ExecutionException, InterruptedException {
		schemaInitialization.setMode(IndexingMode.PARTIAL);
		return indexService.indexingStop();
	}

	@PostMapping("/indexPage")
	public ResponseEntity<?> indexPage(@NotNull HttpServletRequest request) throws Exception {
		return indexService.indexingPageStart(request);
	}

}
