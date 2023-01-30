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
	private final IndexResponse indexResponse = new IndexResponse();
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<Thread>();
	private static Future<String> future;
	private volatile boolean allowed = true;
	private static ParseSite parseSite;


	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(SitesList sitesList) throws Exception {
		ForkJoinPool fjpPool = new ForkJoinPool();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		allowed = true;
		siteRepository.deleteAll();
		commonRepository.resetIndex();
		logger.warn("Initialization of `site` table");
		siteRepository.saveAll(fillEntity.initSiteEntity());

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (!allowed) {
					fjpPool.shutdownNow();
					executor.shutdownNow();
					break;
				}
				Callable<String> siteTask = () -> {
					IndexTask rootIndexTask = new IndexTask(site.getUrl(), site);
					parseSite = new ParseSite(rootIndexTask, site);
					parseSite.setAllowed(true);
					logger.info("Invoke " + site.getName() + " " + site.getUrl());
					fjpPool.invoke(parseSite);
					return "Done";
				};
				try {
					future = executor.submit(siteTask);
					future.get();
				} catch (InterruptedException | RuntimeException | ExecutionException e) {
					logger.warn("Faulty submit task to executor");
					e.printStackTrace();
				}
				if (future.isDone() && allowed) {
					siteRepository.updateSiteStatus("INDEXED", site.getName());
					siteRepository.updateStatusTime(site.getName(), LocalDateTime.now());
					logger.info("Status of site " + site.getName() + " set to INDEXED");
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
		parseSite.setAllowed(false);
		siteRepository.updateAllSitesStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}
}
