package searchengine.services.indexing;

import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.safety.Safelist;
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
import java.util.*;
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
	private static Site site;
	@Autowired
	private static SitesList sitesList;
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
		pageRepository.deleteAll();
		siteRepository.deleteAll();
		siteRepository.resetIdOnSite();
		siteRepository.saveAll(fillEntity.initSiteTable());

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (allowed) {
					SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
					try {
						long time = System.currentTimeMillis();
						future = executor.submit(() -> forkSiteTask(fjpPool, site, siteEntity));
						future.get();
						logger.warn("--- " + site.getUrl() + " parsed in " + (System.currentTimeMillis() - time) + " ms");
						logger.warn("--- Site " + site.getUrl() + " contains " + pages.size() + " pages");
					} catch (RuntimeException | ExecutionException | InterruptedException e) {
						logger.error("Error while getting Future " + Thread.currentThread().getStackTrace()[1].getMethodName());
					}
					updateSiteAfterParse(site);
					long time = System.currentTimeMillis();
					pageRepository.saveAll(pages);
					logger.warn("--- site " + site.getUrl() + " DB filled in " + (System.currentTimeMillis() - time) + " ms");
					pages.clear();
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
	public ResponseEntity<?> singleIndexingStart(String url, Site site) throws Exception {
		ForkJoinPool fjpPool = new ForkJoinPool();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		PageEntity pageEntity = pageRepository.findByPath(new URL(url).getPath());
		SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
		if (pageEntity == null) return indexResponse.indexPageFailed();

		pageRepository.delete(pageEntity);
		logger.warn("Удалили запись");

		SitesList singleSiteList = new SitesList();
		List<Site> setOfSite = Collections.singletonList(site);
		singleSiteList.setSites(setOfSite);
		long time1 = System.currentTimeMillis();
		logger.warn("Запускаем индекс этой страницы");
		future = executor.submit(() -> forkSiteTask(fjpPool, site, siteEntity));
		future.get();
		logger.warn("--- " + site.getUrl() + " parsed in " + (System.currentTimeMillis() - time1) + " ms");
		logger.warn("--- Site " + site.getUrl() + " contains " + pages.size() + " pages");
		updateSiteAfterParse(site);
		long time = System.currentTimeMillis();
		pageRepository.saveAll(pages);
		logger.warn("--- site " + site.getUrl() + " DB filled in " + (System.currentTimeMillis() - time) + " ms");
		pages.clear();
		singleTask.get().start();
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
