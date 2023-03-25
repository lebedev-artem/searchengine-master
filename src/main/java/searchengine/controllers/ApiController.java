package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexResponse;
import searchengine.services.indexing.IndexService;
import searchengine.services.indexing.SchemaActions;
import searchengine.services.statistics.StatisticsService;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Setter
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

	private final IndexService indexService;
	private final SchemaActions schemaActions;
	private final IndexResponse indexResponse;
	private final StatisticsService statisticsService;
	TotalStatistics totalStatistics = new TotalStatistics();

	@GetMapping("/statistics")
	public ResponseEntity<StatisticsResponse> statistics() {
		return ResponseEntity.ok(statisticsService.getStatistics());
	}

	@GetMapping("/startIndexing")
	public ResponseEntity<?> startIndexing() throws Exception {
		log.warn("Mapping /startIndexing executed");
		Set<SiteEntity> siteEntities = schemaActions.fullInit();
		if (siteEntities.size() == 0) return indexResponse.startFailedEmptySites();

		return indexService.indexingStart(siteEntities);
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<?> stopIndexing() throws ExecutionException, InterruptedException {
		return indexService.indexingStop();
	}

	@PostMapping("/indexPage")
	public ResponseEntity<?> indexPage(@NotNull HttpServletRequest request) throws Exception {
		schemaActions.partialInit(request);
		return indexService.indexingPageStart(request);
	}

	@PostMapping("/testDeleteSiteWithPages")
	public void test(Integer request) throws Exception {
		indexService.test(request);
	}
}
