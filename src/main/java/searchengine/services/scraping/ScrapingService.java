package searchengine.services.scraping;

import lombok.*;
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
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.services.stuff.AcceptableContentTypes;
import searchengine.services.stuff.StringPool;
import searchengine.services.stuff.StaticVault;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

import static searchengine.services.stuff.Regex.*;

@Getter
@Setter
@Service
@NoArgsConstructor
public class ScrapingService extends RecursiveTask<Boolean> {
	private static final Integer COUNT_PAGES_TO_DROP = 50;
	private static final AcceptableContentTypes ACCEPTABLE_CONTENT_TYPES = new AcceptableContentTypes();
	private static final Logger logger = LogManager.getLogger(ScrapingService.class);
	public static volatile boolean allowed = true;
	private ScrapTask parentTask;
//	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	private Connection.Response jsoupResponse;
	private Document document;

	private Integer parentStatusCode;
	private String parentStatusMessage;
	private String parentContent;
	private String parentPath;
	private String parentUrl;
	private Site site;
	private Integer siteId;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;

	private Future<Integer> future;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private BlockingQueue<PageEntity> queueOfPagesForSaving;
	private BlockingQueue<PageEntity> queueOfPagesForIndexing;

	private PageRepository pageRepository;
	private StringPool stringPool;

	public ScrapingService(ScrapTask scrapTask,
	                       @NotNull Site site,
	                       @NotNull SiteEntity siteEntity,
	                       BlockingQueue<PageEntity> queueOfPagesForSaving,
	                       BlockingQueue<PageEntity> queueOfPagesForIndexing,
	                       PageRepository pageRepository,
	                       StringPool stringPool) {
		this.parentTask = scrapTask;
		this.site = site;
		parentUrl = site.getUrl();
		this.siteEntity = siteEntity;
		this.siteId = siteEntity.getId();
		this.queueOfPagesForSaving = queueOfPagesForSaving;
		this.queueOfPagesForIndexing = queueOfPagesForIndexing;
		this.pageRepository = pageRepository;
		this.stringPool = stringPool;
	}

	@Override
	protected Boolean compute() {
		String urlOfTask = parentTask.getUrl();
		List<ScrapingService> subTasks = new LinkedList<>();

		if (!allowed) return breakScraping(subTasks);

		Connection.Response responseSinglePage = getResponseFromUrl(urlOfTask);
		if (responseSinglePage != null) {
			dropPageToQueue();
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
				if (url.matches(URL_IS_VALID) && href.startsWith(StaticVault.siteUrl) && !newChildLinks.containsKey(href) && !href.equals(url)) {
					if (((HTML_EXT.stream().anyMatch(href.substring(href.length() - 4)::contains) || !href.matches(URL_IS_FILE_LINK)))) {

						String elementPath = href.substring(url.length() - 1);
						synchronized (StringPool.class) {
							if (!stringPool.getPaths().containsKey(elementPath)) {
								stringPool.internPath(elementPath);
								newChildLinks.put(href, parentStatusCode);
							}
						}

					}
				}
			} catch (StringIndexOutOfBoundsException ignored) {
			}
		}
		elements.clear();
		return newChildLinks;
	}

	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {
		try {
			jsoupResponse = Jsoup.connect(url).execute();
			parentUrl = jsoupResponse.url().toString();
			jsoupResponse.bufferUp();
			if (StaticVault.siteUrl.isEmpty()) StaticVault.siteUrl = parentUrl;
		} catch (IOException | UncheckedIOException exception) {
			parentTask.setLastError(exception.getMessage());
			return null;
		}

		if (!ACCEPTABLE_CONTENT_TYPES.contains(jsoupResponse.contentType())) {
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
		}
		return jsoupResponse;
	}

	private void dropPageToQueue() {
		synchronized (stringPool.getAddedPathsToQueue()) {
			if (!stringPool.getAddedPathsToQueue().containsKey(pageEntity.getPath())) {
				try {
					stringPool.internAddedPathToQueue(parentPath);
					queueOfPagesForSaving.put(pageEntity);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			pageEntity = null;
		}
	}

	private void forkTasksFromSubtasks(List<ScrapingService> subTasks, Map<String, Integer> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) return;

		for (String subLink : subLinks.keySet()) {
			if (childIsValidToFork(subLink)) {
				ScrapTask childScrapTask = new ScrapTask(subLink);
				ScrapingService task = new ScrapingService(childScrapTask, site, siteEntity, queueOfPagesForSaving, queueOfPagesForIndexing, pageRepository, stringPool);
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
		return (HTML_EXT.stream().anyMatch(subLink.substring(subLink.length() - 4)::contains))
				| ((!subLink.matches(URL_IS_FILE_LINK))
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
