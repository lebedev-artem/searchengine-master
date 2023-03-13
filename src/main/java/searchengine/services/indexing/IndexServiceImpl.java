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
import searchengine.model.SearchIndexEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusIndexing;
import searchengine.repositories.*;
import searchengine.services.interfaces.IndexService;
import searchengine.services.lemmatization.LemmasCollectingService;
import searchengine.services.queues.PagesSavingService;
import searchengine.services.searchIndexGeneration.IndexGenerationServiceImpl;
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
//@RequiredArgsConstructor
@NoArgsConstructor
public class IndexServiceImpl implements IndexService {

	private static final Logger logger = LogManager.getLogger(IndexService.class);
	private static final Logger rootLogger = LogManager.getRootLogger();
	//	private final IndexResponse indexResponse;
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<>();

	public volatile boolean allowed = true;
	public volatile boolean isStarted = false;
	public static boolean isRanOnce = false;

//	private Integer siteId;
	public static final StringPool stringPool = new StringPool();
	private BlockingQueue<PageEntity> queueOfPagesForLemmasCollecting = new LinkedBlockingQueue<>(1_000);
	private BlockingQueue<PageEntity> queueOfPagesForSaving = new LinkedBlockingQueue<>(1_000);
	private BlockingQueue<SearchIndexEntity> queueOfLemmasForIndexGeneration = new LinkedBlockingQueue<>(3_000);

	@Autowired
	Site site;
	@Autowired
	SitesList sitesList;
	@Autowired
	SiteRepository siteRepository;
	@Autowired
	PageRepository pageRepository;
	@Autowired
	LemmaRepository lemmaRepository;
	@Autowired
	SearchIndexRepository searchIndexRepository;
	@Autowired
	LemmasCollectingService lemmasCollectingService;
	@Autowired
	PagesSavingService pagesSavingService;
	@Autowired
	ScrapingService scrapingService;
	@Autowired
	IndexGenerationServiceImpl indexGenerationService;
	@Autowired
	IndexResponse indexResponse;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(@NotNull Set<SiteEntity> siteEntities) {

		long time = System.currentTimeMillis();
		if (isStarted) return indexResponse.startFailed();

		isStarted = true;
		allowed = true;
		scrapingService.setAllowed(true);

		ForkJoinPool fjpPool = new ForkJoinPool();
//		initSchema(sitesList);

		singleTask.set(new Thread(() -> {
			for (SiteEntity siteEntity : siteEntities) {
				rootLogger.error(siteEntity.toString());
				pagesSavingService.setScrapingIsDone(false);
				lemmasCollectingService.setSavingPagesIsDone(false);
				indexGenerationService.setLemmasCollectingIsDone(false);
				CountDownLatch latch = new CountDownLatch(4);
				ScrapTask rootScrapTask = new ScrapTask(siteEntity.getUrl());
				if (allowed) {
					Thread scrapingThread = new Thread(() -> {
						long timeMain = System.currentTimeMillis();
						startScrapingOfSite(rootScrapTask, fjpPool, siteEntity);
						latch.countDown();
						pagesSavingService.setScrapingIsDone(true);
						StaticVault.pages.clear();
						rootLogger.info(": Scraping of " + siteEntity.getName() + " finished in " + (System.currentTimeMillis() - timeMain) + " ms");
//						rootLogger.warn(": " + stringPool.pathsSize() + " paths processed");
						rootLogger.info("::: scraping-thread finished");
					}, "scrap-thread");

					Thread pagesSaverThread = new Thread(() -> {
						startPagesSaver(siteEntity);
						latch.countDown();
						lemmasCollectingService.setSavingPagesIsDone(true);
						rootLogger.info("::: saving-pages-thread finished");
					}, "saving-thread");

					Thread lemmasCollectorThread = new Thread(() -> {
						startLemmasCollector(siteEntity);
						latch.countDown();
						indexGenerationService.setLemmasCollectingIsDone(true);
						rootLogger.info(":: lemmas-finding-thread finished");
					}, "lemmas-thread");

					Thread indexGeneratorThread = new Thread(() -> {
						startIndexGenerator(siteEntity);
						latch.countDown();
						rootLogger.info(":: index-generation-thread finished");
					}, "index-thread");

					scrapingThread.start();
					pagesSaverThread.start();
					lemmasCollectorThread.start();
					indexGeneratorThread.start();

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
			setIsRanOnce(true);
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

//		indexingStart(singleSiteList);

		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingStop() {
		if (!isStarted) return indexResponse.stopFailed();
		setStarted(false);
		setAllowed(false);
		scrapingService.setAllowed(false);
		pagesSavingService.setIndexingStopped(true);
		lemmasCollectingService.setIndexingStopped(true);
		siteRepository.updateAllStatusStatusTimeError(StatusIndexing.FAILED.status, LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private void startScrapingOfSite(ScrapTask rootScrapTask, @NotNull ForkJoinPool fjpPool, @NotNull SiteEntity siteEntity) {
		scrapingService.setAllowed(true);
		scrapingService = new ScrapingService(rootScrapTask, siteEntity, queueOfPagesForSaving, queueOfPagesForLemmasCollecting, pageRepository, siteRepository);
		rootLogger.error("---------------------------------------------------------");
		rootLogger.info("- Start scraping " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(scrapingService);
	}

	private void startActionsAfterScraping(@NotNull Site site, ScrapTask scrapTask) {
		String status = pageRepository.existsBySiteEntity(siteRepository.findByUrl(site.getUrl())) ? StatusIndexing.INDEXED.status : StatusIndexing.FAILED.status;
		stringPool.getPaths().clear();
		stringPool.getAddedPathsToQueue().clear();
		StaticVault.siteUrl = "";

		if (allowed) {
			siteRepository.updateStatusStatusTimeError(status, LocalDateTime.now(), scrapTask.getLastError(), site.getName());
			rootLogger.info("- Status of site " + site.getName() + " set to " + status);
		}
	}

	@Transactional
	private void initSchema(@NotNull SitesList siteListToInit) throws MalformedURLException {
		if (isRanOnce) {
			searchIndexRepository.deleteAllInBatch();
			lemmaRepository.deleteAllInBatch();
			pageRepository.deleteAllInBatch();
			for (Site s : siteListToInit.getSites()) {
				siteRepository.updateStatusStatusTime(StatusIndexing.INDEXING.status, LocalDateTime.now(), s.getName());
			}
			return;
		}

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
				siteRepository.updateStatusStatusTime(StatusIndexing.INDEXING.status, LocalDateTime.now(), siteListToInit.getSites().get(0).getName());
			} else {
				siteRepository.saveAll(initSiteTable(siteListToInit));
			}

		} else {
			siteRepository.deleteAllInBatch();
//			searchIndexRepository.resetIdOnIndexTable();
//			lemmaRepository.resetIdOnLemmaTable();
//			pageRepository.resetIdOnPageTable();
//			siteRepository.resetIdOnSiteTable();

//			logger.debug(siteRepository.count() + " sites in DB, " + "site id " + siteRepository.findAll().get(0).getId());
			List<SiteEntity> siteEntityList = initSiteTable(sitesList);
			siteRepository.saveAll(siteEntityList);
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

	public void startPagesSaver(SiteEntity siteEntity) {
		pagesSavingService.setIndexingStopped(false);
		pagesSavingService.setQueue(queueOfPagesForSaving);
		pagesSavingService.setQueueForIndexing(queueOfPagesForLemmasCollecting);
		pagesSavingService.setSiteEntity(siteEntity);
		pagesSavingService.pagesSaving();
	}

	public void startLemmasCollector(SiteEntity siteEntity) {
		lemmasCollectingService.setQueue(queueOfPagesForLemmasCollecting);
		lemmasCollectingService.setQueueForIndexGeneration(queueOfLemmasForIndexGeneration);
		lemmasCollectingService.setIndexingStopped(false);
		lemmasCollectingService.setSiteEntity(siteEntity);
		lemmasCollectingService.lemmasIndexGeneration();
	}

	public void startIndexGenerator(SiteEntity siteEntity) {
		indexGenerationService.setIndexingStopped(false);
		indexGenerationService.setQueue(queueOfLemmasForIndexGeneration);
		indexGenerationService.setSiteEntity(siteEntity);
		indexGenerationService.indexGenerate();
	}

	public void setIsRanOnce(boolean value) {
		isRanOnce = value;
	}
}
