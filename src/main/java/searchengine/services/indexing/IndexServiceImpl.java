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
import searchengine.services.lemmatization.LemmaFinderPageable;
import searchengine.services.queues.PagesSavingService;
import searchengine.services.stuff.StringPool;
import searchengine.services.stuff.TempStorage;
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

	private static Future<?> futureForScrapingSite;
	public volatile boolean allowed = true;
	public volatile boolean isStarted = false;
	private static ScrapingService scrapingService;
	private Integer siteId;
	public final StringPool stringPool;

	private final Site site;
	private final SitesList sitesList;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final SearchIndexRepository searchIndexRepository;
	private BlockingQueue<PageEntity> queueOfPagesForIndexing = new LinkedBlockingQueue<>(100);
	private BlockingQueue<PageEntity> queueOfPagesForSaving = new LinkedBlockingQueue<>(100);

	@Autowired
	LemmaFinderPageable lemmaFinderPageable;
	@Autowired
	PagesSavingService pagesSavingService;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(@NotNull SitesList sitesList) throws MalformedURLException {
		long timeMain = System.currentTimeMillis();
		if (isStarted) return indexResponse.startFailed();

		isStarted = true;
		allowed = true;
		pagesSavingService.setScrapingFutureIsDone(false);
		ForkJoinPool fjpPool = new ForkJoinPool();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		initSchema(sitesList);

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (allowed) {
					ScrapTask rootScrapTask = new ScrapTask(site.getUrl());
					siteId = siteRepository.findByName(site.getName()).getId();
					try {
						futureForScrapingSite = executor.submit(() -> startScrapingOfSite(rootScrapTask, fjpPool, site, siteId));
//						taskSavingPages.set(new Thread(() -> startSavingPages(site), "PagesSaving thread"));
//						taskLemmasFinding.set(new Thread(() -> startLemmaFinder(site), "Lemmas-Finding"));

//						taskSavingPages.get().start();
//						taskLemmasFinding.get().start();

						futureForScrapingSite.get();
					} catch (RuntimeException | ExecutionException | InterruptedException e) {
						logger.error("Error while getting Future " + Thread.currentThread().getStackTrace()[1].getMethodName());
						e.printStackTrace();
					}
//					pageRepository.saveAllAndFlush(TempStorage.pages);
//					rootLogger.info("~ Site " + site.getUrl() + " contains " + pageRepository.countBySiteId(siteId) + " pages");
					startActionsAfterScraping(site, rootScrapTask);
					rootLogger.info(":: Scraping of " + site.getName() + " finished in " + (System.currentTimeMillis() - timeMain) + " ms," + " future isDone - " + futureForScrapingSite.isDone());

//					try {
//						rootLogger.info("Lemmas finding started");
//						long timeLemmas = System.currentTimeMillis();
//					futureForLemmaFinder = executor.submit(() -> startLemmaFinder();
//						futureForLemmaFinder.get();
//						rootLogger.info("Lemmas finding finished in " + (System.currentTimeMillis() - timeLemmas) + " ms");
//						rootLogger.info(lemmaRepository.countAllLemmas() + " lemmas in DB");
//						rootLogger.warn("--------------------------------------------------------------------------------------");
//					} catch (InterruptedException | ExecutionException e) {
//						e.printStackTrace();
//					}

				} else {
					try {
						Thread.sleep(3133);
					} catch (InterruptedException e) {
						logger.error("I don't want to sleep -> " + Thread.currentThread().getStackTrace()[1].getMethodName());
					} finally {
						fjpPool.shutdownNow();
						executor.shutdownNow();
					}
					break;
				}
			}
			isStarted = false;
			pagesSavingService.setScrapingFutureIsDone(true);
			executor.shutdownNow();
			fjpPool.shutdownNow();
//			rootLogger.warn("::: Parsing finished in " + (System.currentTimeMillis() - timeMain) + " ms, isStarted - " + isStarted + " isAllowed - " + allowed);
		}, "Main-thread"));
		singleTask.get().start();


//		taskSavingPages.set(new Thread(this::startSavingPages, "Pages-saving"));
//		taskSavingPages.get().start();

		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingPageStart(@NotNull HttpServletRequest request) throws MalformedURLException {
		SingleSiteListCreator singleSiteListCreator = new SingleSiteListCreator(sitesList);
		String url = request.getParameter("url");
//		String path = new URL(url).getPath();
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
		siteRepository.updateAllStatusStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private void startScrapingOfSite(ScrapTask rootScrapTask, @NotNull ForkJoinPool fjpPool, @NotNull Site site, int siteId) {
		scrapingService = new ScrapingService(rootScrapTask, this, site, siteRepository.findById(siteId), queueOfPagesForSaving, queueOfPagesForIndexing);
		scrapingService.setAllowed(true);
		rootLogger.info(":: Invoke " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(scrapingService);
	}

	private void startActionsAfterScraping(@NotNull Site site, ScrapTask scrapTask) {
		String status = pageRepository.existsBySiteEntity(siteRepository.findByName(site.getName())) ? "INDEXED" : "FAILED";
		stringPool.getPaths().clear();
//		TempStorage.pages.clear();
		TempStorage.siteUrl = "";
//		TempStorage.nowOnMapPages = 0;
		if (futureForScrapingSite.isDone() && allowed) {
			siteRepository.updateStatusStatusTimeError(status, LocalDateTime.now(), scrapTask.getLastError(), site.getName());
			rootLogger.info(":: Status of site " + site.getName() + " set to " + status);
//			rootLogger.info("~ Table page contains " + pageRepository.countAllPages() + " pages");
		}

		System.gc();
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
			siteRepository.resetIdOnSiteTable();
			pageRepository.resetIdOnPageTable();
			lemmaRepository.resetIdOnLemmaTable();
			siteRepository.saveAllAndFlush(initSiteTable(sitesList));
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

	private void startLemmaFinder(Site site) {
		lemmaFinderPageable.runThroughQueue(queueOfPagesForIndexing, futureForScrapingSite, site);
	}

	private void startSavingPagesService() {
		taskSavingPages.set(new Thread(() -> {
			pagesSavingService.setQueue(queueOfPagesForSaving);
			pagesSavingService.pagesSaving();
		}, "saving-pages"));

		taskSavingPages.get().start();
	}
}
