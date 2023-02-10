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
import searchengine.repositories.*;
import searchengine.services.interfaces.EntityService;
import searchengine.services.interfaces.IndexService;
import searchengine.services.parsing.ParseSiteService;
import searchengine.services.parsing.ParseTask;
import searchengine.services.utilities.SingleSiteListCreator;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.HttpURLConnection;
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

	private final EntityService entityService;
	private static final Logger logger = LogManager.getLogger(IndexService.class);
	private final IndexResponse indexResponse = new IndexResponse();
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<>();
	private static Future<Integer> future;
	public volatile boolean allowed = true;
	public volatile boolean isStarted = false;
	private static ParseSiteService parseSiteService;
	private static SingleSiteListCreator singleSiteListCreator;
	private HashMap<String, Integer> links = new HashMap<>();
	private Set<PageEntity> pages = new HashSet<>();

	private HashMap<String, Integer> linksNeverDelete = new HashMap<>();
	private Set<PageEntity> pagesNeverDelete = new HashSet<>();

	private static Site site;
	private static SitesList sitesList;
	@Autowired
	private final SiteRepository siteRepository;
	@Autowired
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

		if (sitesList.getSites().size() > 1){
			pageRepository.deleteAll();
			siteRepository.deleteAll();
			siteRepository.resetIdOnSiteTable();
			siteRepository.saveAllAndFlush(entityService.initSiteTable(sitesList));
		}

		singleTask.set(new Thread(() -> {
			for (Site site : sitesList.getSites()) {
				if (allowed) {
					int siteId = siteRepository.findEntityByName(site.getName()).getId();
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
					updateSiteAfterParse(site);
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
	public ResponseEntity<?> singleIndexingStart(@NotNull HttpServletRequest request) throws MalformedURLException {
		setStarted(true);
		String url = request.getParameter("url");
		String hostName = url.substring(0, url.indexOf(new URL(url).getPath())+1);
		String path = new URL(url).getPath();

		Site site = sitesList.getSites().stream()
				.filter(s -> hostName.equals(s.getUrl()))
				.findAny()
				.orElse(null);
		PageEntity pageEntity = pageRepository.findByPath(path);

		if ((site == null) || (pageEntity == null)) return indexResponse.indexPageFailed();

		siteRepository.updateAllSitesStatusTimeError("INDEXING", LocalDateTime.now(), getLastErrorMsg(url));
		entityService.deleteAllPagesByPath(path);

		SitesList singleSiteList = singleSiteListCreator.getSiteList(url, site);

		indexingStart(singleSiteList);

		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingStop(){
		if (!isStarted) return indexResponse.stopFailed();

		logger.warn("~ Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started");
		setStarted(false);
		setAllowed(false);
		siteRepository.updateAllSitesStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	private int forkSiteTask(ForkJoinPool fjpPool, Site site, int siteId) {
		logger.warn("~ Method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> started");
		ParseTask rootParseTask = new ParseTask(site.getUrl());
		parseSiteService = new ParseSiteService(rootParseTask,this, site, siteId, siteRepository.findEntityById(siteId));
		parseSiteService.setAllowed(true);
		logger.warn("~ Invoke " + site.getName() + " " + site.getUrl());
		fjpPool.invoke(parseSiteService);
		return rootParseTask.getLinksOfTask().size();
	}

	private void updateSiteAfterParse(Site site) {
		//надо опдумать записывать ли коллекицю если прервали стопом
//		Если прервалось эксепщеном то FAILED
		// Если failed посчитать колва страниц и если меньше к примеру 10 то файлуд
		if (future.isDone() && allowed) {
			siteRepository.updateSiteStatus("INDEXED", site.getName());
			siteRepository.updateStatusTime(site.getName(), LocalDateTime.now());
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


	@Override
	public Boolean getStarted() {
		return isStarted;
	}

	@Override
	public Boolean getAllowed() {
		return allowed;
	}

}
