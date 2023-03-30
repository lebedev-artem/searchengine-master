package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.SitesList;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.exceptionhandler.EmptyQueryException;
import searchengine.model.SiteEntity;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.*;
import searchengine.services.search.SearchService;
import searchengine.services.statistics.StatisticsService;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
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
	private final IndexingActions indexingActions;
	private final SearchService searchService;

	@GetMapping("/statistics")
	public ResponseEntity<StatisticsResponse> statistics() {
		log.warn("Mapping /statistics executed");
		return ResponseEntity.ok(statisticsService.getStatistics());
	}

	@GetMapping("/startIndexing")
	public ResponseEntity<?> startIndexing() throws Exception {
		log.warn("Mapping /startIndexing executed");
		if (indexingActions.getIndexingActionsStarted())
			return indexResponse.startFailed();
		Set<SiteEntity> siteEntities = schemaActions.fullInit();
		if (siteEntities.size() == 0) return indexResponse.startFailedEmptySites();

		return indexService.indexingStart(siteEntities);
	}

	@PostMapping("/indexPage")
	public ResponseEntity<?> indexPage(@NotNull HttpServletRequest request) throws Exception {
		log.warn("Mapping /indexPage executed");
		if (indexingActions.getIndexingActionsStarted())
			return indexResponse.startFailed();
		if (request.getParameter("url") == null)
			return indexResponse.startFailedEmptySites();
		SiteEntity siteEntity = schemaActions.partialInit(request);
		if (siteEntity == null) return indexResponse.indexPageFailed();
		return indexService.indexingPageStart(siteEntity);
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<?> stopIndexing() throws ExecutionException, InterruptedException {
		log.warn("Mapping /stopIndexing executed");
		return indexService.indexingStop();
	}

	@PostMapping("/testDeleteSiteWithPages")
	public void test(Integer request) throws Exception {
		indexService.test(request);
	}

	@GetMapping("/search")
	public ResponseEntity<SearchResponse> search(
			@RequestParam String query,
			@RequestParam(required = false) String site,
			@RequestParam Integer offset,
			@RequestParam Integer limit){

		return ResponseEntity.ok(searchService.getSearchResults(query, site, offset, limit));
	}
}
