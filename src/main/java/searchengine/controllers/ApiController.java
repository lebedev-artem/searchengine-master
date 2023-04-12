package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.repositories.PageRepository;
import searchengine.services.indexing.*;

import searchengine.dto.search.SearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.services.search.SearchService;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.statistics.StatisticsService;

@Slf4j
@Setter
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

	private final SearchService searchService;
	private final IndexingService indexingService;
	private final StatisticsService statisticsService;
	private final PageRepository pageRepository;

	@GetMapping("/statistics")
	public ResponseEntity<StatisticsResponse> statistics() {
		return ResponseEntity.ok(statisticsService.getStatistics());
	}

	@GetMapping("/startIndexing")
	public ResponseEntity<IndexingResponse> startIndexing() {
		return indexingService.indexingStart();
	}

	@PostMapping("/indexPage")
	public ResponseEntity<IndexingResponse> indexPage(@RequestParam final String url) {
		return indexingService.indexingPageStart(url);
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<IndexingResponse> stopIndexing() {
		return indexingService.indexingStop();
	}

	@GetMapping("/search")
	public ResponseEntity<SearchResponse> search(
			@RequestParam final String query,
			@RequestParam(required = false) final String site,
			@RequestParam final Integer offset,
			@RequestParam final Integer limit) {

		return ResponseEntity.ok(searchService.getSearchResults(query, site, offset, limit));
	}

	@PostMapping("/test")
	public ResponseEntity<?> test(@RequestParam final int id){
		pageRepository.deleteById(id);
		return new ResponseEntity<>(HttpStatus.ACCEPTED);
	}
}
