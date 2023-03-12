package searchengine.services.indexing;

import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.*;
import searchengine.services.interfaces.IndexService;
import searchengine.services.lemmatization.LemmasIndexService;
import searchengine.services.queues.PagesSavingService;
import searchengine.services.stuff.StringPool;
import searchengine.services.stuff.StaticVault;
import searchengine.services.scraping.ScrapingService;
import searchengine.services.scraping.ScrapTask;
import searchengine.services.stuff.SingleSiteListCreator;

import javax.servlet.http.HttpServletRequest;
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

	private static final Logger logger = LogManager.getLogger(IndexService.class);
	private static final Logger rootLogger = LogManager.getRootLogger();
	private final IndexResponse indexResponse;
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<>();
	private static final ThreadLocal<Thread> taskSavingPages = new ThreadLocal<>();
	private static final ThreadLocal<Thread> taskLemmasFinding = new ThreadLocal<>();

	//	private static Future<?> futureForScrapingSite;
	public volatile boolean allowed = true;
	public volatile boolean isStarted = false;
	//	private static ScrapingService scrapingService;
	private Integer siteId;
	public final StringPool stringPool;

	private final Site site;
	private final SitesList sitesList;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final SearchIndexRepository searchIndexRepository;
	private BlockingQueue<PageEntity> queueOfPagesForIndexing = new LinkedBlockingQueue<>(500);
	private BlockingQueue<PageEntity> queueOfPagesForSaving = new LinkedBlockingQueue<>(1000);


	@Autowired
	LemmasIndexService lemmasIndexService;
	@Autowired
	PagesSavingService pagesSavingService;
	@Autowired
	ScrapingService scrapingService;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(@NotNull SitesList sitesList) throws MalformedURLException {
		long time = System.currentTimeMillis();
		if (isStarted) return indexResponse.startFailed();

		isStarted = true;
		allowed = true;
		scrapingService.setAllowed(true);

		ForkJoinPool fjpPool = new ForkJoinPool();
		initSchema(sitesList);
		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				siteId = siteRepository.findByName(site.getName()).getId();

				pagesSavingService.setScrapingIsDone(false);
				lemmasIndexService.setSavingPagesIsDone(false);
				CountDownLatch latch = new CountDownLatch(3);
				ScrapTask rootScrapTask = new ScrapTask(site.getUrl());
				if (allowed) {
					Thread scrapingThread = new Thread(() -> {
						long timeMain = System.currentTimeMillis();
						startScrapingOfSite(rootScrapTask, fjpPool, site, siteId);
						latch.countDown();
						pagesSavingService.setScrapingIsDone(true);
						StaticVault.pages.clear();
						rootLogger.info(": Scraping of " + site.getName() + " finished in " + (System.currentTimeMillis() - timeMain) + " ms");
//						rootLogger.warn(": " + stringPool.pathsSize() + " paths processed");
						rootLogger.info("::: scraping-thread finished");
					}, "scrap-thread");

					Thread savingPagesThread = new Thread(() -> {
						startSavingPagesService(site);
						latch.countDown();
						lemmasIndexService.setSavingPagesIsDone(true);
						rootLogger.info("::: saving-pages-thread finished");
					}, "saving-thread");

					Thread lemmasFindingThread = new Thread(() -> {
						startLemmasIndexFinder(site);
						latch.countDown();
						rootLogger.info(":: lemmas-finding-thread finished");
					}, "lemmas-thread");

					scrapingThread.start();
					savingPagesThread.start();
					lemmasFindingThread.start();

					try {
						latch.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					startActionsAfterScraping(site, rootScrapTask);
				} else {
					try {
						Thread.sleep(5_000);
					} catch (InterruptedException e) {
						logger.error("I don't want to sleep -> " + Thread.currentThread().getStackTrace()[1].getMethodName());
					} finally {
						shutDownService(fjpPool);
					}
					break;
				}
			}
			shutDownService(fjpPool);
			rootLogger.warn(siteRepository.count() + " site(s)");
			rootLogger.warn(pageRepository.count() + " pages");
			rootLogger.warn(lemmaRepository.count() + " lemmas");
			rootLogger.warn(searchIndexRepository.count() + " index entries");
			rootLogger.warn("Just in " + (System.currentTimeMillis() - time) + " ms");
			rootLogger.warn("- I'm ready to start again and again");
			System.gc();
		}, "start-thread"));

		singleTask.get().start();
		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingPageStart(@NotNull HttpServletRequest request) throws MalformedURLException {
		SingleSiteListCreator singleSiteListCreator = new SingleSiteListCreator(sitesList);
		String url = request.getParameter("url");
		String hostName = new URL(url).getHost();

		SitesList singleSiteList = singleSiteListCreator.getSiteList(url, hostName);

		if (singleSiteList == null)
			return indexResponse.indexPageFailed();

		indexingStart(singleSiteList);

		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingStop() {
		if (!isStarted) return indexResponse.stopFailed();
		setStarted(false);
		setAllowed(false);
		scrapingService.setAllowed(false);
		pagesSavingService.setIndexingStopped(true);
		lemmasIndexService.setIndexingStopped(true);
		siteRepository.updateAllStatusStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private void startScrapingOfSite(ScrapTask rootScrapTask, @NotNull ForkJoinPool fjpPool, @NotNull Site site, int siteId) {
		scrapingService.setAllowed(true);
		scrapingService = new ScrapingService(rootScrapTask, site, siteRepository.findById(siteId), queueOfPagesForSaving, queueOfPagesForIndexing, pageRepository, stringPool);
		rootLogger.error("---------------------------------------------------------");
		rootLogger.info("- Start scraping " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(scrapingService);
	}

	private void startActionsAfterScraping(@NotNull Site site, ScrapTask scrapTask) {
		String status = pageRepository.existsBySiteEntity(siteRepository.findByName(site.getName())) ? "INDEXED" : "FAILED";
		stringPool.getPaths().clear();
		stringPool.getAddedPathsToQueue().clear();
		StaticVault.siteUrl = "";

		if (allowed) {
			siteRepository.updateStatusStatusTimeError(status, LocalDateTime.now(), scrapTask.getLastError(), site.getName());
			rootLogger.info("- Status of site " + site.getName() + " set to " + status);
		}
	}

	private void initSchema(@NotNull SitesList siteListToInit) throws MalformedURLException {
		for (Site s : siteListToInit.getSites()) {
			if (s.getUrl().startsWith("www"))
				s.setUrl("https://".concat(s.getUrl()));
			if (s.getUrl().lastIndexOf("/") != (s.getUrl().length() - 1)) {
				s.setUrl(s.getUrl().concat("/"));
			}
		}

		if ((sitesList.getSites().size() > 1) && (siteListToInit.getSites().size() == 1)) {
			SiteEntity siteEntity = siteRepository.findByName(siteListToInit.getSites().get(0).getName());
			if (siteEntity != null) {
				String path = new URL(siteListToInit.getSites().get(0).getUrl()).getPath();
				pageRepository.deletePagesBySiteIdContainingPath(path, siteEntity.getId());
				siteRepository.updateStatusStatusTime("INDEXING", LocalDateTime.now(), siteListToInit.getSites().get(0).getName());
			} else {
				siteRepository.saveAllAndFlush(initSiteTable(siteListToInit));
			}

		} else {
			siteRepository.deleteAllInBatch();
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			searchIndexRepository.resetIdOnIndexTable();
			lemmaRepository.resetIdOnLemmaTable();
			pageRepository.resetIdOnPageTable();
			siteRepository.resetIdOnSiteTable();

//			logger.debug(siteRepository.count() + " sites in DB, " + "site id " + siteRepository.findAll().get(0).getId());
			List<SiteEntity> siteEntityList = initSiteTable(sitesList);
			siteRepository.saveAllAndFlush(siteEntityList);
			logger.debug(siteRepository.count() + " sites in DB, " + "site id " + siteRepository.findAll().get(0).getId() + " init site table");

		}
	}

	private @NotNull List<SiteEntity> initSiteTable(@NotNull SitesList sl) {
		List<SiteEntity> siteEntities = new ArrayList<>();
		for (Site site : sl.getSites()) siteEntities.add(initSiteRow(site));
		return siteEntities;
	}

	private @NotNull SiteEntity initSiteRow(@NotNull Site s) {
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setStatus("INDEXING");
		siteEntity.setStatusTime(LocalDateTime.now());
		siteEntity.setLastError("");
		siteEntity.setUrl(s.getUrl());
		siteEntity.setName(s.getName());
		return siteEntity;
	}

	@Override
	public Boolean isAllowed() {
		return allowed;
	}

	private void shutDownService(@NotNull ForkJoinPool fjpPool) {
		isStarted = false;
		allowed = false;
		fjpPool.shutdownNow();
	}

	public void startLemmasIndexFinder(Site site) {
		lemmasIndexService.setQueue(queueOfPagesForIndexing);
		lemmasIndexService.setIndexingStopped(false);
		lemmasIndexService.setSite(site);
		lemmasIndexService.lemmasIndexGeneration();

	}

	public void startSavingPagesService(Site site) {
		pagesSavingService.setIndexingStopped(false);
		pagesSavingService.setQueue(queueOfPagesForSaving);
		pagesSavingService.setQueueForIndexing(queueOfPagesForIndexing);
		pagesSavingService.setSite(site);
		pagesSavingService.pagesSaving();

	}
}
