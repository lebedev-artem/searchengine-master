package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.services.indexing.*;
import searchengine.services.search.SearchService;
import searchengine.services.statistics.StatisticsService;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.*;

@Slf4j
@Setter
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

	private final IndexingService indexingService;
	private final SchemaActions schemaActions;
	private final IndexResponse indexResponse;
	private final StatisticsService statisticsService;
	private final IndexingActions indexingActions;
	private final SearchService searchService;
	private final PageRepository pageRepository;

	@GetMapping("/statistics")
	public ResponseEntity<StatisticsResponse> statistics() {
		return ResponseEntity.ok(statisticsService.getStatistics());
	}

	@GetMapping("/startIndexing")
	public ResponseEntity<?> startIndexing(){
		return indexingService.indexingStart();
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
		return indexingService.indexingPageStart(siteEntity);
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<?> stopIndexing() throws ExecutionException, InterruptedException {
		log.warn("Mapping /stopIndexing executed");
		return indexingService.indexingStop();
	}

	@PostMapping("/testDeleteSiteWithPages")
	public void test(Integer request) throws Exception {
//		Page<PageEntity> page = pageRepository.findByIdAndSiteEntity()
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
