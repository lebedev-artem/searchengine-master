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
import searchengine.repositories.CommonRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.ShutdownService;
import searchengine.services.interfaces.FillEntity;
import searchengine.services.interfaces.IndexService;

import java.time.LocalDateTime;
import java.util.concurrent.*;

@Transactional
@Component
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {
	@Autowired
	private final SiteRepository siteRepository;
	@Autowired
	private CommonRepository commonRepository;
	@Autowired
	private FillEntity fillEntity;
	private static final Logger logger = LogManager.getLogger(IndexService.class);
	private ForkJoinPool fjpPool = new ForkJoinPool();
	private IndexResponse indexResponse = new IndexResponse();
	Thread singleTask;
	Future<String> future;
	ExecutorService executor = Executors.newSingleThreadExecutor();
	private volatile boolean started = false;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(SitesList sitesList) throws Exception {
		singleTask = new Thread(() -> {
			siteRepository.deleteAll();
			commonRepository.resetIndex();
			logger.info("Initialization of `site` table");
			siteRepository.saveAll(fillEntity.initSiteEntity());
			for (Site site : sitesList.getSites()) {
				if (!singleTask.isInterrupted()) {
					Callable<String> siteTask = () -> {
						IndexTask rootIndexTask = new IndexTask(site.getUrl(), site);
						ParseSite parseSite = new ParseSite(rootIndexTask, site);
						fjpPool.invoke(parseSite);
						logger.info("Invoke " + site.getName() + " " + site.getUrl());
						return "Done";
					};
					future = executor.submit(siteTask);
					try {
						future.get();
					} catch (InterruptedException | RuntimeException | ExecutionException e) {
						logger.warn(e.getMessage());
					}
					if (future.isDone()) {
						siteRepository.updateSiteStatus("INDEXED", site.getName());
						siteRepository.updateStatusTime(site.getName(), LocalDateTime.now());
						logger.info("Status of site " + site.getName() + " set to INDEXED");
					}
				}
			}
			logger.warn("All sites was indexed");
			fjpPool.shutdown();
		});
		singleTask.start();
		return indexResponse.successfully();
	}

	@Override
	public void indexingStop() throws ExecutionException, InterruptedException {
		ShutdownService shutdownService = new ShutdownService();
		future.cancel(true);
		logger.warn("future isCanceled - " + future.isCancelled());
		singleTask.interrupt();
		logger.warn("singleTask isInterrupted - " + singleTask.isInterrupted());
		shutdownService.stop(executor);
		logger.warn("executor.isShutdown() - " + executor.isShutdown());
		shutdownService.stop(fjpPool);
		logger.warn("fjpPool.isShutdown() - " + executor.isShutdown());
		siteRepository.updateAllSitesStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
	}
}
