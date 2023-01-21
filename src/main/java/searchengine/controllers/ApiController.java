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
import searchengine.services.indexing.IndexResponse;
import searchengine.services.indexing.IndexService;
import searchengine.services.StatisticsService;
import searchengine.services.utilities.FillEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
	private IndexResponse indexResponse;
	private List<Thread> runningTasks = new ArrayList<Thread>();
	private ExecutorService poolSites;

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
//        siteRepository.deleteAll();
//        siteRepository.resetIndex();
//        siteRepository.flush();


		List<Site> sites = sitesList.getSites();
		poolSites = Executors.newFixedThreadPool(sites.size());
		if (runningTasks.size() != 0) {
			return new ResponseEntity<>(new IndexResponse(false, "Индексация уже запущена"), HttpStatus.BAD_REQUEST);
		}
		logger.warn("@GetMapping (\"/startIndexing) running");

		for (Site s : sites) {
			poolSites.execute(new Runnable() {
				@Override
				public void run() {
					indexService.indexingStart(s);
				}
			});

//            Thread thread = new Thread(parsingOneSite);
//            runningTasks.add(thread);
//            thread.start();
//        }
//
//        for (Site s : sites) {
//            Runnable parsingOneSite = () -> indexService.indexingStart(s);
//            Thread thread = new Thread(parsingOneSite);
//            runningTasks.add(thread);
//            thread.start();
//        }

		}
		return new ResponseEntity<>(new IndexResponse(true, ""), HttpStatus.OK);
	}

	@GetMapping("/stopIndexing")
	public ResponseEntity<?> stopIndexing() {
		logger.warn("@GetMapping (\"/stopIndexing) running");
//		if (runningTasks.size() == 0) {
//			return new ResponseEntity<>(new IndexResponse(false, "Индексация не запущена"), HttpStatus.BAD_REQUEST);
//		}

        indexService.indexingStop();
//		poolSites.shutdown();
//        try {
//            if (!poolSites.awaitTermination(60, TimeUnit.SECONDS)) {
//                poolSites.shutdownNow();
//                if (!poolSites.awaitTermination(60, TimeUnit.SECONDS))
//                    System.err.println("Pool did not terminate");
//            }
//        } catch (InterruptedException ie) {
//            poolSites.shutdownNow();
//            Thread.currentThread().interrupt();
//        }

		return new ResponseEntity<>(new IndexResponse(true, ""), HttpStatus.OK);
	}
}
