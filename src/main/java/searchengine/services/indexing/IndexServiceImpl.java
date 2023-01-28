package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.repositories.SiteRepository;
import searchengine.services.ShutdownService;
import searchengine.services.interfaces.IndexService;
import searchengine.services.utilities.FillEntityImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Transactional
@Component
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {
	@Autowired
	private final SiteRepository siteRepository;
	@Autowired
	private FillEntityImpl fillEntityImpl;
	private static final Logger logger = LogManager.getLogger(IndexService.class);
	private ForkJoinPool pool = new ForkJoinPool();
	List<CompletableFuture<Void>> futures = new ArrayList<CompletableFuture<Void>>();
	private ExecutorService poolOfSites;
	private IndexResponse indexResponse = new IndexResponse();
	Thread t;


	public ForkJoinPool getPool() {
		return pool;
	}

	@Override
	@Transactional
	public ResponseEntity<?> indexingStart(SitesList sitesList) throws ExecutionException, InterruptedException {

		ExecutorService executor = Executors.newFixedThreadPool(10);

		t = new Thread(() -> {
			for (Site site : sitesList.getSites()) {

				logger.info("Status of site " + site.getName() + " set to INDEXING");
				Runnable siteTask = () -> {
					IndexTask rootIndexTask = new IndexTask(site.getUrl(), site);
					ParseSite parseSite = new ParseSite(rootIndexTask, site);
					pool.invoke(parseSite);
					logger.info("Invoke " + site.getName() + " " + site.getUrl());

				};
				executor.execute(siteTask);
				Future<String> result = executor.submit(siteTask, "DONE");
				try {
					result.get();
				} catch (InterruptedException | RuntimeException | ExecutionException e) {
//					e.printStackTrace();
					logger.warn(e.getMessage());
				}
				if (result.isDone() == true) {
					siteRepository.changeSiteStatus("INDEXED", site.getName());
					logger.info("Status of site " + site.getName() + " set to INDEXED");
				}

			}
		});
		t.start();

		logger.warn("All sites was indexed");
		return indexResponse.successfully();
	}

	@Override
	public void indexingStop() {
		ShutdownService shutdownService = new ShutdownService();
		t.interrupt();
		shutdownService.stop(pool);

	}
}
