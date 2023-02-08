package searchengine.services.indexing;

import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.*;
import searchengine.services.interfaces.FillEntity;
import searchengine.services.interfaces.IndexService;
import searchengine.services.parsing.ParseSiteService;
import searchengine.services.parsing.ParseTask;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Setter
@Getter
@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {

	private final FillEntity fillEntity;
	private static final Logger logger = LogManager.getLogger(IndexService.class);
	private final IndexResponse indexResponse = new IndexResponse();
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<>();
	private static Future<Integer> future;
	public volatile boolean allowed = true;
	public volatile boolean isStarted = false;
	@Autowired
	private static ParseSiteService parseSiteService;
	private HashMap<String, Integer> links = new HashMap<>();
	private Set<PageEntity> pages = new HashSet<>();
	@Autowired
	private final SiteRepository siteRepository;
	@Autowired
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final SearchIndexRepository searchIndexRepository;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(SitesList sitesList) throws Exception {
		long timeMain = System.currentTimeMillis();
		isStarted = true;
		ForkJoinPool fjpPool = new ForkJoinPool();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		pageRepository.deleteAll();
		siteRepository.deleteAll();
		siteRepository.resetIdOnSite();
		logger.warn("--- Initialization of `site` table ---");
		siteRepository.saveAll(fillEntity.initSiteTable());

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (allowed) {
					SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
					try {
//						Запускаем парсинг ссылок
						long time = System.currentTimeMillis();
						future = executor.submit(() -> forkSiteTask(fjpPool, site, siteEntity));
						logger.warn("--- Site " + site.getUrl() + " was parsed with " + future.get() + " links. ---");
						logger.warn("--- Site " + site.getUrl() + " contains " + links.size() + " links with codes");
						logger.warn("--- Site " + site.getUrl() + " contains " + pages.size() + " pages");
						logger.warn("--- " + site.getUrl() + " parsed in " + (System.currentTimeMillis() - time) + " ms");
					} catch (InterruptedException | RuntimeException | ExecutionException e) {
						logger.error("Error in adding a task to the pool");
						logger.error("Error in future.get()");
						e.printStackTrace();
					}
//					Запускаем индексацию страниц


//					Записываем в таблицу site статусы и время
					updateSiteAfterParse(site);
					logger.warn("--- Start add pages to DB from " + site.getUrl() + " ---");
					long time = System.currentTimeMillis();
					pageRepository.saveAll(pages);
					logger.warn("--- site " + site.getUrl() + " DB filled in " + (System.currentTimeMillis() - time) + " ms");
				} else {
					fjpPool.shutdownNow();
					executor.shutdownNow();
					break;
				}
			}
			isStarted = false;
			logger.info("--- Parsing finished in " + (System.currentTimeMillis() - timeMain) + " ms ---");
		}));
		singleTask.get().start();
		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> singleIndexingStart(String url) throws MalformedURLException {
//		добавить проверку ссылки
		String path = new URL(url).getPath();
		PageEntity pageEntity = new PageEntity();
		pageEntity = pageRepository.findByPath(path);
		if (pageEntity == null) return indexResponse.indexPageFailed();
		pageRepository.delete(pageEntity);
		logger.warn("Удалили запись");
		logger.warn("Запускаем индекс этой странцы");

		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException {
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		setStarted(false);
		setAllowed(false);
		siteRepository.updateAllSitesStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private int forkSiteTask(ForkJoinPool fjpPool, Site site, SiteEntity siteEntity) {
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		ParseTask rootParseTask = new ParseTask(site.getUrl());
		parseSiteService = new ParseSiteService(rootParseTask, this, site, siteEntity);
		parseSiteService.setAllowed(true);
		logger.info("Invoke " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(parseSiteService);
		return rootParseTask.getLinksOfTask().size();
	}

	private void updateSiteAfterParse(Site site) {
		logger.warn("--- Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started---");
		//надо опдумать записывать ли коллекицю если прервали стопом
		if (future.isDone() && allowed) {
			siteRepository.updateSiteStatus("INDEXED", site.getName());
			siteRepository.updateStatusTime(site.getName(), LocalDateTime.now());
			logger.info("Status of site " + site.getName() + " set to INDEXED");
		}
	}

	@Override
	public Boolean getStarted() {
		return isStarted;
	}

	@Override
	public Boolean getAllowed() {
		return allowed;
	}
}
