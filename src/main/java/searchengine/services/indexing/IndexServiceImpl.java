package searchengine.services.indexing;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.repositories.SiteEntityRepository;
import searchengine.services.interfaces.FillEntity;
import searchengine.services.interfaces.IndexService;

import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {

	private final FillEntity fillEntity;
	private static final Logger logger = LogManager.getLogger(IndexService.class);
	private final IndexResponse indexResponse = new IndexResponse();
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<Thread>();
	private static Future<Integer> future;
	private volatile boolean allowed = true;
	@Autowired
	private static ParseSiteService parseSiteService;
//	private final SiteEntityRepository siteEntityRepository;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(SitesList sitesList) throws Exception {
		ForkJoinPool fjpPool = new ForkJoinPool();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		allowed = true;
//		siteEntityRepository.deleteAll();
//		siteEntityRepository.resetIndex();
		logger.warn("Initialization of `site` table");
//		siteEntityRepository.saveAll(fillEntity.initSiteEntity());

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (allowed) {
					try {
						future = executor.submit(() -> forkSiteTask(fjpPool, site));
						logger.warn("Site " + site.getUrl() + " was parsed with " + future.get() + " links.");
					} catch (InterruptedException | RuntimeException | ExecutionException e) {
						logger.error("Error in adding a task to the pool");
						logger.error("Error in future.get()");
						logger.error(e.getStackTrace());
					}
					updateSiteAfterParse(site);
				} else {
					fjpPool.shutdownNow();
					executor.shutdownNow();
					break;
				}
			}
			logger.info("Exit from thread");
		}));
		singleTask.get().start();
		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException {
		allowed = false;
		parseSiteService.setAllowed(false);
//		siteEntityRepository.updateAllSitesStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private static int forkSiteTask(ForkJoinPool fjpPool, Site site) {
		IndexTask rootIndexTask = new IndexTask(site.getUrl(), site);
		parseSiteService = new ParseSiteService(rootIndexTask, site);
		parseSiteService.setAllowed(true);
		logger.info("Invoke " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(parseSiteService);
		return rootIndexTask.getLinksOfTask().size();
	}

	private void updateSiteAfterParse(Site site) {
		if (future.isDone() && allowed) {
//			siteEntityRepository.updateSiteStatus("INDEXED", site.getName());
//			siteEntityRepository.updateStatusTime(site.getName(), LocalDateTime.now());
			logger.info("Status of site " + site.getName() + " set to INDEXED");
		}
	}
}
