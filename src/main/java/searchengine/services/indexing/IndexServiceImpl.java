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
import searchengine.model.*;
import searchengine.repositories.*;
import searchengine.services.lemmatization.LemmasCollectingService;
import searchengine.services.queues.PagesSavingService;
import searchengine.services.searchIndexGeneration.IndexCollectingServiceImpl;
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
//	public static boolean isRanOnce = false;

	//	private Integer siteId;
	public static final StringPool stringPool = new StringPool();
	private BlockingQueue<PageEntity> queueOfPagesForLemmasCollecting = new LinkedBlockingQueue<>(10_000);
	private BlockingQueue<PageEntity> queueOfPagesForSaving = new LinkedBlockingQueue<>(1_000);
	private BlockingQueue<SearchIndexEntity> queueOfLemmasForIndexGeneration = new LinkedBlockingQueue<>(100_000);

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
	IndexCollectingServiceImpl indexGenerationService;
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
		singleTask.set(new Thread(() -> {

			for (SiteEntity siteEntity : siteEntities) {
				CountDownLatch latch = new CountDownLatch(4);
				ScrapTask rootScrapTask = new ScrapTask(siteEntity.getUrl());
				if (allowed) {
					Thread scrapingThread = new Thread(() -> {
						long timeMain = System.currentTimeMillis();
						startScrapingOfSite(rootScrapTask, fjpPool, siteEntity);
						latch.countDown();
						pagesSavingService.setScrapingIsDone(true);
						scrapingService.getStringPool().paths.clear();
						scrapingService.getStringPool().addedPathsToQueue.clear();
						System.gc();
//						StaticVault.pages.clear();
						rootLogger.info(": Scraping of " + siteEntity.getName() + " finished in " + (System.currentTimeMillis() - timeMain) + " ms");
						rootLogger.info("::: scraping-thread finished, latch =  " + latch.getCount());
					}, "scrap-thread");

					Thread pagesSaverThread = new Thread(() -> {
						startPagesSaver(siteEntity);
						latch.countDown();
						lemmasCollectingService.setSavingPagesIsDone(true);
//						StaticVault.pages.clear();
						rootLogger.info("::: saving-pages-thread finished, latch =  " + latch.getCount());
					}, "pages-thread");

					Thread lemmasCollectorThread = new Thread(() -> {
						startLemmasCollector(siteEntity);
						latch.countDown();
						indexGenerationService.setLemmasCollectingIsDone(true);
						rootLogger.info(":: lemmas-finding-thread finished, latch =  " + latch.getCount());
					}, "lemmas-thread");

					Thread indexGeneratorThread = new Thread(() -> {
						startIndexGenerator(siteEntity);
						latch.countDown();
						rootLogger.info(":: index-generation-thread finished, latch =  " + latch.getCount());
					}, "indexes-thread");

					scrapingThread.start();
					pagesSaverThread.start();
					lemmasCollectorThread.start();
					indexGeneratorThread.start();

					try {
						latch.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					startActionsAfterScraping(siteEntity, rootScrapTask);
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
			rootLogger.error("FINISHED. I'm ready to start again and again");
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
		indexGenerationService.setIndexingStopped(true);
		siteRepository.updateAllStatusStatusTimeError(IndexingStatus.FAILED.status, LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private void startScrapingOfSite(ScrapTask rootScrapTask, @NotNull ForkJoinPool fjpPool, @NotNull SiteEntity siteEntity) {
		scrapingService.setAllowed(true);
		scrapingService = new ScrapingService(rootScrapTask, siteEntity, queueOfPagesForSaving, queueOfPagesForLemmasCollecting, pageRepository, siteRepository);
		rootLogger.error("---------------------------------------------------------");
		rootLogger.info("- Start scraping " + siteEntity.getName() + " " + siteEntity.getUrl());
		fjpPool.invoke(scrapingService);
	}

	private void startActionsAfterScraping(@NotNull SiteEntity siteEntity, ScrapTask scrapTask) {
		String status = pageRepository.existsBySiteEntity(siteEntity) ? IndexingStatus.INDEXED.status : IndexingStatus.FAILED.status;
		stringPool.getPaths().clear();
		stringPool.getAddedPathsToQueue().clear();
		StaticVault.siteUrl = "";

		if (allowed) {
			siteRepository.updateStatusStatusTimeErrorByUrl(status, LocalDateTime.now(), scrapTask.getLastError(), siteEntity.getUrl());
			rootLogger.info("- Status of site " + siteEntity.getName() + " set to " + status);
		}
	}

	public boolean isAllowed() {
		return allowed;
	}

	private void shutDownService(@NotNull ForkJoinPool fjpPool) {
		isStarted = false;
		allowed = false;
		fjpPool.shutdownNow();
	}

	public void startPagesSaver(SiteEntity siteEntity) {
		pagesSavingService.setScrapingIsDone(false);
		pagesSavingService.setIndexingStopped(false);
		pagesSavingService.setQueue(queueOfPagesForSaving);
		pagesSavingService.setQueueForIndexing(queueOfPagesForLemmasCollecting);
		pagesSavingService.setSiteEntity(siteEntity);
		pagesSavingService.pagesSaving();
	}

	public void startLemmasCollector(SiteEntity siteEntity) {
		lemmasCollectingService.setQueue(queueOfPagesForLemmasCollecting);
		lemmasCollectingService.setSavingPagesIsDone(false);
		lemmasCollectingService.setQueueOfSearchIndexEntities(queueOfLemmasForIndexGeneration);
		lemmasCollectingService.setIndexingStopped(false);
		lemmasCollectingService.setSiteEntity(siteEntity);
		lemmasCollectingService.lemmasIndexGeneration();
	}

	public void startIndexGenerator(SiteEntity siteEntity) {
		indexGenerationService.setIndexingStopped(false);
		indexGenerationService.setLemmasCollectingIsDone(false);
		indexGenerationService.setQueue(queueOfLemmasForIndexGeneration);
		indexGenerationService.setSiteEntity(siteEntity);
		indexGenerationService.indexCollect();
	}

	public void test(Long id){

		pageRepository.deleteAllInBatch(pageRepository.findAllBySiteEntity(siteRepository.getReferenceById(id)));
//		pageRepository.deleteById(id);

	}
}
