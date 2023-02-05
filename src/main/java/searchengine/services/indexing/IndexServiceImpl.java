package searchengine.services.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.repositories.*;
import searchengine.services.interfaces.FillEntity;
import searchengine.services.interfaces.IndexService;
import searchengine.services.parsing.ParseSiteService;
import searchengine.services.parsing.ParseTask;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.*;

@Setter
@Getter
@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {

	private final FillEntity fillEntity;
	private static final Logger logger = LogManager.getLogger(IndexService.class);
	private final IndexResponse indexResponse = new IndexResponse();
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<Thread>();
	private static Future<Integer> future;
	private volatile boolean allowed = true;

	private volatile boolean isStarted = false;
	@Autowired
	private static ParseSiteService parseSiteService;
	private HashMap<String, Integer> mainLinks = new HashMap<>();
	private Set<PageEntity> pages = new HashSet<>();

	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final SearchIndexRepository searchIndexRepository;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(SitesList sitesList) throws Exception {
		ForkJoinPool fjpPool = new ForkJoinPool();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		allowed = true;
		isStarted = true;
		siteRepository.deleteAll();
		siteRepository.resetIdOnSite();
		logger.warn("--- Initialization of `site` table ---");
		siteRepository.saveAll(fillEntity.initSiteTable());

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (allowed) {
					try {
//						Запускаем парсинг ссылок
						future = executor.submit(() -> forkSiteTask(fjpPool, site));
						logger.warn("--- Site " + site.getUrl() + " was parsed with " + future.get() + " links. ---");
						logger.warn("--- Site " + site.getUrl() + " contains " + mainLinks.size() + " links with codes");
						logger.warn("--- Site " + site.getUrl() + " contains " + pages.size() + " pages");
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
		isStarted = false;
		parseSiteService.setAllowed(false);
		siteRepository.updateAllSitesStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private int forkSiteTask(ForkJoinPool fjpPool, Site site) {
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		ParseTask rootParseTask = new ParseTask(site.getUrl());
		parseSiteService = new ParseSiteService(rootParseTask, this);
		parseSiteService.setAllowed(true);
		logger.info("Invoke " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(parseSiteService);
		return rootParseTask.getLinksOfTask().size();
	}

	private void updateSiteAfterParse(Site site) {
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		if (future.isDone() && allowed) {
			siteRepository.updateSiteStatus("INDEXED", site.getName());
			siteRepository.updateStatusTime(site.getName(), LocalDateTime.now());
			logger.info("Status of site " + site.getName() + " set to INDEXED");
		}
	}


}
