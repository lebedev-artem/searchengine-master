package searchengine.services.indexing;

import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
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
import searchengine.services.parsing.ParseSiteService;
import searchengine.services.parsing.ParseTask;
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
	private static ParseSiteService parseSiteService;
	private HashMap<String, Integer> links = new HashMap<>();
	private Set<PageEntity> pages = new HashSet<>();
	private String lastError;
	private Integer siteId;

	private HashMap<String, Integer> linksNeverDelete = new HashMap<>();
	private ArrayList<PageEntity> pagesNeverDelete = new ArrayList<>();
	private final Site site;
	private final SitesList sitesList;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final SearchIndexRepository searchIndexRepository;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(@NotNull SitesList sitesList){
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
					siteId = siteRepository.findByName(site.getName()).getId();
					try {
						long time = System.currentTimeMillis();
						future = executor.submit(() -> forkSiteTask(fjpPool, site, siteId));
						future.get();
						logger.warn("~ " + site.getUrl() + " parsed in " + (System.currentTimeMillis() - time) + " ms");
						logger.warn("~ Site " + site.getUrl() + " contains " + pages.size() + " pages");
					} catch (RuntimeException | ExecutionException | InterruptedException e) {
						logger.error("Error while getting Future " + Thread.currentThread().getStackTrace()[1].getMethodName());
						e.printStackTrace();
					}
					long time = System.currentTimeMillis();
					pageRepository.saveAll(pages);
					logger.warn("~ Site " + site.getUrl() + " DB filled in " + (System.currentTimeMillis() - time) + " ms");
					pages.clear();
					links.clear();
					updateSiteAfterParse(siteId, site);
				} else {
					fjpPool.shutdownNow();
					executor.shutdownNow();
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
		SingleSiteListCreator singleSiteListCreator = new SingleSiteListCreator();
		String url = request.getParameter("url");
		String path = new URL(url).getPath();
		String hostName = url.substring(0, url.indexOf(new URL(url).getPath())+1);

		PageEntity pageEntity = pageRepository.findByPath(path);
		SitesList singleSiteList = singleSiteListCreator.getSiteList(url, hostName);;

		if ((singleSiteList == null) || (pageEntity == null)) return indexResponse.indexPageFailed();

		if (linksNeverDelete.size() > 0 & pagesNeverDelete.size() > 0){
			linksNeverDelete.forEach((key, value) -> {if (key.equals(url)) linksNeverDelete.remove(key);});
			pagesNeverDelete.removeIf(p -> p.getPath().contains(path));
		}

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

	private int forkSiteTask(ForkJoinPool fjpPool, Site site, int siteId) {
		logger.warn("~ Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started");
		ParseTask rootParseTask = new ParseTask(site.getUrl());
		parseSiteService = new ParseSiteService(rootParseTask,this, site, siteId, siteRepository.findById(siteId));
		parseSiteService.setAllowed(true);
		logger.warn("~ Invoke " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(parseSiteService);
		return rootParseTask.getLinksOfTask().size();
	}

	private void updateSiteAfterParse(Integer siteId, Site site) {
		boolean countPagesEnough = false;
//				pageRepository.findAllBySiteId(siteId).size() > 35;
		int count = 0;
		for (PageEntity p : pagesNeverDelete.stream().toList()) {
			if (p.getSiteEntity().getId() == siteId) count++;
			if (count == 35) {
				countPagesEnough = true;
				break;
			}
		}
		if (future.isDone() && allowed) {
			siteRepository.updateStatusStatusTimeError(countPagesEnough ? "INDEXED" : "FAILED", LocalDateTime.now(), lastError, site.getName());
			logger.warn("~ Status of site " + site.getName() + " set to INDEXED");
		}
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

	private void initSchema(SitesList sitesList) {

//		добавить проверку есть ли сайт в базе
		if (sitesList.getSites().size() == 1){
			if (!siteRepository.existsByName(sitesList.getSites().get(0).getName())) siteRepository.save(initSiteRow(sitesList.getSites().get(0)));
			pageRepository.deletePagesContainingPath(sitesList.getSites().get(0).getUrl());

//			siteRepository.updateStatusStatusTimeError("INDEXING", LocalDateTime.now(), getLastErrorMsg(sitesList.getSites().get(0).getUrl()), site.getName());

		} else {
			pageRepository.deleteAllInBatch();
			siteRepository.deleteAllInBatch();
			siteRepository.resetIdOnSiteTable();
			pageRepository.resetIdOnPageTable();
			siteRepository.saveAllAndFlush(initSiteTable(sitesList));
		}

	}

	private List<SiteEntity> initSiteTable(@NotNull SitesList sitesList) {
		List<SiteEntity> siteEntities = new ArrayList<>();
		for (Site site : sitesList.getSites()) siteEntities.add(initSiteRow(site));
		return siteEntities;
	}

	private SiteEntity initSiteRow(@NotNull Site site) {
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setStatus("INDEXING");
		siteEntity.setStatusTime(LocalDateTime.now());
		siteEntity.setLastError("");
		siteEntity.setUrl(site.getUrl());
		siteEntity.setName(site.getName());
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
	public Boolean getStarted() {
		return isStarted;
	}

	@Override
	public Boolean getAllowed() {
		return allowed;
	}

}
