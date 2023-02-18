package searchengine.services.indexing;

import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.*;
import searchengine.services.interfaces.IndexService;
import searchengine.services.parsing.TempStorage;
import searchengine.services.parsing.ScrapingService;
import searchengine.services.parsing.ScrapTask;
import searchengine.services.utilities.SingleSiteListCreator;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
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
	private final IndexResponse indexResponse;
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<>();
	private static Future<Integer> future;
	public volatile boolean allowed = true;
	public volatile boolean isStarted = false;
	private static ScrapingService scrapingService;
	private Integer siteId;

	private final Site site;
	private final SitesList sitesList;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final SearchIndexRepository searchIndexRepository;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(@NotNull SitesList sitesList) throws MalformedURLException {
		long timeMain = System.currentTimeMillis();
		if (isStarted) return indexResponse.startFailed();

		isStarted = true;
		allowed = true;
		ForkJoinPool fjpPool = new ForkJoinPool();
		ExecutorService executor = Executors.newSingleThreadExecutor();

		initSchema(sitesList);

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (allowed) {
					ScrapTask rootScrapTask = new ScrapTask(site.getUrl());
					siteId = siteRepository.findByName(site.getName()).getId();
					try {
						long time = System.currentTimeMillis();
						future = executor.submit(() -> invokeScrapingSite(rootScrapTask, fjpPool, site, siteId));
						future.get();
						logger.warn("~ " + site.getUrl() + " parsed in " + (System.currentTimeMillis() - time) + " ms");
					} catch (RuntimeException | ExecutionException | InterruptedException e) {
						logger.error("Error while getting Future " + Thread.currentThread().getStackTrace()[1].getMethodName());
						e.printStackTrace();
					}
					long time = System.currentTimeMillis();
//					pageRepository.saveAll(TempStorage.pages);
//					logger.warn("~ Site " + site.getUrl() + " DB filled in " + (System.currentTimeMillis() - time) + " ms");
					pageRepository.saveAll(TempStorage.pages);
					logger.warn("~ Site " + site.getUrl() + " contains " + pageRepository.findAllBySiteId(siteId).size() + " pages");
					TempStorage.paths.clear();
					TempStorage.pages.clear();
					TempStorage.urls.clear();
					ScrapingService.countPages = 0;
					doAfterScraping(siteId, site, rootScrapTask);
				} else {
					try {
						Thread.sleep(2222);
					} catch (InterruptedException e) {
						logger.warn("I don't want to sleep -> " + Thread.currentThread().getStackTrace()[1].getMethodName());
					} finally {
						fjpPool.shutdownNow();
						executor.shutdownNow();
					}
					break;
				}
			}
			isStarted = false;
			logger.warn("~ Parsing finished in " + (System.currentTimeMillis() - timeMain) + " ms, isStarted - " + isStarted + " isAllowed - " + allowed +" ---");
		}));
		singleTask.get().start();
		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingPageStart(@NotNull HttpServletRequest request) throws MalformedURLException {
		SingleSiteListCreator singleSiteListCreator = new SingleSiteListCreator(sitesList);
		String url = request.getParameter("url");
		String path = new URL(url).getPath();
		String hostName = url.substring(0, url.indexOf(new URL(url).getPath())+1);

		PageEntity pageEntity = pageRepository.findByPath(path);
		SitesList singleSiteList = singleSiteListCreator.getSiteList(url, hostName);

		if ((singleSiteList == null) || (pageEntity == null)) return indexResponse.indexPageFailed();

//		if (linksNeverDelete.size() > 0 & pagesNeverDelete.size() > 0){
//			linksNeverDelete.forEach((key, value) -> {if (key.equals(url)) linksNeverDelete.remove(key);});
//			pagesNeverDelete.removeIf(p -> p.getPath().contains(path));
//		}

		indexingStart(singleSiteList);

		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingStop(){
		if (!isStarted) return indexResponse.stopFailed();

		logger.warn("~ Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started");
		setStarted(false);
		setAllowed(false);
		siteRepository.updateAllStatusStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private int invokeScrapingSite(ScrapTask rootScrapTask, @NotNull ForkJoinPool fjpPool, @NotNull Site site, int siteId) {
		logger.warn("~ Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started");
//		ParseTask rootParseTask = new ParseTask(site.getUrl());
		scrapingService = new ScrapingService(rootScrapTask,this, site, siteId, siteRepository.findById(siteId));
		scrapingService.setAllowed(true);
		logger.warn("~ Invoke " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(scrapingService);
		return rootScrapTask.getLinks().size();
	}

	private void doAfterScraping(Integer siteId, @NotNull Site site, ScrapTask scrapTask) {
		String status = pageRepository.existsBySiteEntity(siteRepository.findByName(site.getName())) ? "INDEXED" : "FAILED";

		if (future.isDone() && allowed) {
			siteRepository.updateStatusStatusTimeError(status, LocalDateTime.now(), scrapTask.getLastError(), site.getName());
			logger.warn("~ Status of site " + site.getName() + " set to " + status);
		}
	}

	public void storeInDatabase(Set<PageEntity> pageEntities){
		pageRepository.saveAllAndFlush(pageEntities);
	}

	private String getLastErrorMsg(String url) {
		Connection.Response response;
		String error = "";
		try {
			response = Jsoup.connect(url).execute();
		} catch (IOException exception) {
			error = exception.getMessage();
		}
		return error;
	}

	private void initSchema(SitesList sl) throws MalformedURLException {
		Site siteToUpdate = sl.getSites().get(0);
		String path = new URL(siteToUpdate.getUrl()).getPath();
		if ((sitesList.getSites().size() > 1) && (sl.getSites().size() == 1)) {
			pageRepository.deletePagesContainingPath(path);
			siteRepository.updateStatusStatusTime("INDEXING", LocalDateTime.now(), siteToUpdate.getName());
		} else {
			pageRepository.deleteAllInBatch();
			pageRepository.resetIdOnPageTable();
			siteRepository.deleteAllInBatch();
			siteRepository.resetIdOnSiteTable();
			siteRepository.saveAllAndFlush(initSiteTable(sitesList));
		}

	}

	private List<SiteEntity> initSiteTable(@NotNull SitesList sl) {
		List<SiteEntity> siteEntities = new ArrayList<>();
		for (Site site : sl.getSites()) siteEntities.add(initSiteRow(site));
		return siteEntities;
	}

	private SiteEntity initSiteRow(@NotNull Site s) {
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setStatus("INDEXING");
		siteEntity.setStatusTime(LocalDateTime.now());
		siteEntity.setLastError("");
		siteEntity.setUrl(s.getUrl());
		siteEntity.setName(s.getName());
		return siteEntity;
	}

	private void deleteAllPagesByPath(String path) {
		List<PageEntity> pagesToDel = pageRepository.findAll();
		int count = 0;
		for (PageEntity page : pagesToDel) {
			if (page.getPath().contains(path)) {
				pageRepository.delete(page);
				logger.warn("~ " + page.getId() + " " + page.getPath() + " deleted");
				count++;
			}
		}
		logger.warn(count + " pages deleted from search_engine.page");
	}

	@Override
	public Boolean isStarted() {
		return isStarted;
	}

	@Override
	public Boolean isAllowed() {
		return allowed;
	}

}
