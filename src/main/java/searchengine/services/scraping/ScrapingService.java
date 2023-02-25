package searchengine.services.scraping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UncheckedIOException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.context.properties.ConfigurationProperties;
import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.indexing.IndexServiceImpl;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static searchengine.services.utilities.Regex.*;

@Getter
@Setter
@AllArgsConstructor
public class ScrapingService extends RecursiveTask<Boolean> {
	private static final Integer COUNT_PAGES_TO_DROP = 20;
	private static final Logger logger = LogManager.getLogger(ScrapingService.class);
	public static volatile boolean allowed = true;
	private final IndexServiceImpl indexService;
	private final AcceptableContentTypes acceptableContentTypes = new AcceptableContentTypes();
	private final ScrapTask parentTask;
	private final ReadWriteLock lock = new ReentrantReadWriteLock();


	private Connection.Response jsoupResponse;
	private Document document;

	private Integer parentStatusCode;
	private String parentStatusMessage;
	private String parentContent;
	private String parentPath;
	private String parentUrl;
	private final Site site;
	private final Integer siteId;
	private final SiteEntity siteEntity;
	private PageEntity pageEntity;

	Future<Integer> future;
	ExecutorService executor = Executors.newSingleThreadExecutor();


	public ScrapingService(ScrapTask scrapTask, IndexServiceImpl indexService, @NotNull Site site, @NotNull SiteEntity siteEntity) {
		this.parentTask = scrapTask;
		this.indexService = indexService;
		this.site = site;
		parentUrl = site.getUrl();
		this.siteEntity = siteEntity;
		this.siteId = siteEntity.getId();
	}

	@Override
	protected Boolean compute() {
		String urlOfTask = parentTask.getUrl();
		List<ScrapingService> subTasks = new LinkedList<>();

		if (!indexService.isAllowed()) return breakScraping(subTasks);

		Connection.Response responseSinglePage = getResponseFromUrl(urlOfTask);
		if (responseSinglePage != null) {
			dropPageToMap();
		} else return true;

		if (jsoupResponse == null)
			jsoupResponse = getResponseFromUrl(urlOfTask);

		Map<String, Integer> childLinksOfTask = getChildLinks(urlOfTask, document);

		forkTasksFromSubtasks(subTasks, childLinksOfTask);
		joinTasksFromSubtasks(subTasks);

		System.gc();
		return true;
	}

	public synchronized Map<String, Integer> getChildLinks(String url, Document document) {
		Map<String, Integer> newChildLinks = new HashMap<>();
		if (document == null) return newChildLinks;
		Elements elements = document.select("a[href]");
		if (elements.isEmpty()) return newChildLinks;

		for (Element element : elements) {
			String href = getHrefFromElement(element);

			try {
				if (url.matches(regexUrlIsValid) && href.startsWith(TempStorage.siteUrl) && !newChildLinks.containsKey(href) && !href.equals(url)) {
					if (((htmlExt.stream().anyMatch(href.substring(href.length() - 4)::contains) || !href.matches(regexUrlIsFileLink)))) {
						String elementPath = href.substring(url.length() - 1);
						synchronized (indexService.getPageRepository()) {
							if (!indexService.getStringPool().getPaths().containsKey(elementPath)) {
								indexService.getStringPool().interPath(elementPath);
								newChildLinks.put(href, parentStatusCode);
							}

//							if (indexService.getPageRepository().existsByPath(elementPath)) {
//								continue;
//							}
						}

					}
				}
			} catch (StringIndexOutOfBoundsException ignored) {
			}
		}
		elements.clear();
		System.gc();
		return newChildLinks;
	}

	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {
		try {
			jsoupResponse = Jsoup.connect(url).execute();
			parentUrl = jsoupResponse.url().toString();
			jsoupResponse.bufferUp();
			if (TempStorage.siteUrl.isEmpty()) TempStorage.siteUrl = parentUrl;
		} catch (IOException | UncheckedIOException exception) {
			parentTask.setLastError(exception.getMessage());
			return null;
		}

		if (!acceptableContentTypes.contains(jsoupResponse.contentType())) {
			return null;
		} else {
			try {
				document = jsoupResponse.parse();
				parentPath = new URL(url).getPath();
				if (parentPath.equals("")) parentPath = "/";
			} catch (IOException e) {
				return null;
			}
			pageEntity = new PageEntity(siteEntity, jsoupResponse.statusCode(), document.html(), parentPath);
//			parentContent = document.html();
//			parentStatusCode = jsoupResponse.statusCode();
//			parentStatusMessage = jsoupResponse.statusMessage();
		}
		return jsoupResponse;
	}

	private void dropPageToMap() {
		synchronized (TempStorage.class) {
			if (TempStorage.pages.stream().noneMatch(x -> x.getPath().equals(parentPath)))
				if (!indexService.getPageRepository().existsByPathAndSiteEntity(parentPath, siteEntity)) {
					TempStorage.pages.add(pageEntity);
					TempStorage.nowOnMapPages++;
					pageEntity = null;
				}
		}

		if (Objects.equals(TempStorage.pages.size(), COUNT_PAGES_TO_DROP)) {
			synchronized (TempStorage.class) {
				try {
					future = executor.submit(this::dropMapToDB);
					logger.warn(future.get() + " more pages have been saved to DB");
				} catch (InterruptedException | ExecutionException e) {
					logger.error("Exception in " + Thread.currentThread().getStackTrace()[1].getMethodName());
				} finally {
					TempStorage.pages.clear();
					TempStorage.nowOnMapPages = 0;
					System.gc();
				}
			}
		}
		executor.shutdownNow();
	}

	private synchronized Integer dropMapToDB() {
		try {
			indexService.getPageRepository().saveAllAndFlush(TempStorage.pages);
			Thread.sleep(10);
		} catch (InterruptedException e) {
			logger.error("Exception while saving TempStorage.pages to DB in method " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}
		return indexService.getPageRepository().countBySiteId(siteId);
	}

	private void forkTasksFromSubtasks(List<ScrapingService> subTasks, Map<String, Integer> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) return;

		for (String subLink : subLinks.keySet()) {
			if (childIsValidToFork(subLink)) {
				ScrapTask childScrapTask = new ScrapTask(subLink);
				ScrapingService task = new ScrapingService(childScrapTask, indexService, site, siteEntity);
				task.fork();
				subTasks.add(task);
				parentTask.addChildTask(childScrapTask);
			}
		}
	}

	private void joinTasksFromSubtasks(List<ScrapingService> childTasks) {
		if (childTasks != null) for (ScrapingService task : childTasks) task.join();
	}

	private static boolean childIsValidToFork(@NotNull String subLink) {
		return (htmlExt.stream().anyMatch(subLink.substring(subLink.length() - 4)::contains))
				| ((!subLink.matches(regexUrlIsFileLink))
				&& (!subLink.contains("#")));
	}

	private @NotNull Boolean breakScraping(List<ScrapingService> subTasks) {
		joinTasksFromSubtasks(subTasks);
		subTasks.clear();
		return true;
	}

	public String getHrefFromElement(Element element) {
		return (element != null) ? element.absUrl("href") : "";
	}

	public void setAllowed(boolean allowed) {
		ScrapingService.allowed = allowed;
	}
}
