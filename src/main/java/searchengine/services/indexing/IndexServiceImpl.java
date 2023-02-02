package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.repositories.*;
import searchengine.services.interfaces.FillEntity;
import searchengine.services.interfaces.IndexService;

import java.time.LocalDateTime;
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
	private final SiteEntityRepository siteEntityRepository;
	private final PageEntityRepository pageEntityRepository;
	private  final LemmaEntityRepository lemmaEntityRepository;
	private final SearchIndexEntityRepository searchIndexEntityRepository;


	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(SitesList sitesList) throws Exception {
		ForkJoinPool fjpPool = new ForkJoinPool();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		allowed = true;
		siteEntityRepository.deleteAll();
		siteEntityRepository.resetIdOnSite();
		logger.warn("--- Initialization of `site` table ---");
		siteEntityRepository.saveAll(fillEntity.initSiteTable());

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (allowed) {
					try {
//						Запускаем парсинг ссылок
						future = executor.submit(() -> forkSiteTask(fjpPool, site));
						logger.warn("--- Site " + site.getUrl() + " was parsed with " + future.get() + " links. ---");
					} catch (InterruptedException | RuntimeException | ExecutionException e) {
						logger.error("Error in adding a task to the pool");
						logger.error("Error in future.get()");
						e.printStackTrace();
					}
//					Запускаем индексацию страниц


//					Записываем в таблицу site статусы и время
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
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		allowed = false;
		parseSiteService.setAllowed(false);
		siteEntityRepository.updateAllSitesStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private static int forkSiteTask(ForkJoinPool fjpPool, Site site) {
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		IndexTask rootIndexTask = new IndexTask(site.getUrl(), site);
		parseSiteService = new ParseSiteService(rootIndexTask, site);
		parseSiteService.setAllowed(true);
		logger.info("Invoke " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(parseSiteService);
		return rootIndexTask.getLinksOfTask().size();
	}

	private void updateSiteAfterParse(Site site) {
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		if (future.isDone() && allowed) {
			siteEntityRepository.updateSiteStatus("INDEXED", site.getName());
			siteEntityRepository.updateStatusTime(site.getName(), LocalDateTime.now());
			logger.info("Status of site " + site.getName() + " set to INDEXED");
		}
	}
}
