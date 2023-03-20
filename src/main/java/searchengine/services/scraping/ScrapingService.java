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
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexServiceImpl;
import searchengine.services.stuff.AcceptableContentTypes;
import searchengine.services.stuff.StringPool;
import searchengine.services.stuff.StaticVault;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	long timeStart;

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

	private BlockingQueue<PageEntity> queueOfPagesForSaving;
	private BlockingQueue<Integer> queueOfPagesForIndexing;

	private PageRepository pageRepository;
	private SiteRepository siteRepository;
	private StringPool stringPool;

	public ScrapingService(ScrapTask scrapTask,
	                       @NotNull SiteEntity siteEntity,
	                       BlockingQueue<PageEntity> queueOfPagesForSaving,
	                       BlockingQueue<Integer> queueOfPagesForIndexing,
	                       PageRepository pageRepository,
	                       SiteRepository siteRepository) {
		this.parentTask = scrapTask;
		this.siteEntity = siteEntity;
		this.siteId = siteEntity.getId();
		this.queueOfPagesForSaving = queueOfPagesForSaving;
		this.queueOfPagesForIndexing = queueOfPagesForIndexing;
		this.pageRepository = pageRepository;
		this.siteRepository = siteRepository;
		this.stringPool = IndexServiceImpl.stringPool;
		parentUrl = siteEntity.getUrl();
	}

	@Override
	protected Boolean compute() {
		timeStart = System.currentTimeMillis();
		String urlOfTask = parentTask.getUrl();
		List<ScrapingService> subTasks = new LinkedList<>();

		if (!allowed) return breakScraping(subTasks);

		Connection.Response responseSinglePage = getResponseFromUrl(urlOfTask);
		if (responseSinglePage != null) {
			dropPageToQueue(urlOfTask);
		} else return true;

		if (jsoupResponse == null)
			jsoupResponse = getResponseFromUrl(urlOfTask);

		Map<String, Integer> childLinksOfTask = getChildLinks(urlOfTask, document);

		forkTasksFromSubtasks(subTasks, childLinksOfTask);
		joinTasksFromSubtasks(subTasks);
//		System.out.println("method compute - " + (System.currentTimeMillis() - timeStart) + " ms");
		return true;
	}

	public synchronized Map<String, Integer> getChildLinks(String url, Document document) {
		long t = System.currentTimeMillis();
		Map<String, Integer> newChildLinks = new HashMap<>();
		if (document == null) return newChildLinks;
		Elements elements = document.select("a[href]");
		if (elements.isEmpty()) return newChildLinks;

		for (Element element : elements) {
			String href = getHrefFromElement(element).toLowerCase(Locale.ROOT);
			try {
				if (pageRepository.existsByPathAndSiteEntity(new URL(href).getPath(), siteEntity)) continue;

//				if (href.endsWith("jpg")) {
//					System.out.println("jpg");
//				}
				//можно добавить проверку чтоб на уровень вниз не уходить, цикличность
				if (url.matches(URL_IS_VALID)
						&& href.startsWith(StaticVault.siteUrl)
						&& !href.contains("#")
						&& !href.equals(url)
						&& !newChildLinks.containsKey(href)
						&& (HTML_EXT.stream().anyMatch(href.substring(href.length() - 4)::contains)
						|| !href.matches(URL_IS_FILE_LINK))) {
//					if
//					{

//						synchronized (StringPool.class) {
					//Здесь можно еще добавить проверку по репозиторию
//						lock.writeLock().lock();
					if (!pageRepository.existsByPathAndSiteEntity(new URL(href).getPath(), siteEntity)) {
						newChildLinks.put(href, parentStatusCode);
					}
//						lock.writeLock().unlock();
//							if (!stringPool.getPaths().containsKey(href)) {
//								stringPool.internPath(href);
//								newChildLinks.put(href, parentStatusCode);
//							}
//						}
//					}
				}
			} catch (StringIndexOutOfBoundsException | MalformedURLException ignored) {
			}
		}
//		System.out.println("method getChildLnks - " + (System.currentTimeMillis() - t) + " ms");
		return newChildLinks;
	}

	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {
		long t = System.currentTimeMillis();
		try {
			if (pageRepository.existsByPathAndSiteEntity(new URL(url).getPath(), siteEntity))
				return null;

			jsoupResponse = Jsoup.connect(url).execute();
//			jsoupResponse.bufferUp();
			if (!ACCEPTABLE_CONTENT_TYPES.contains(jsoupResponse.contentType())) {
				return null;

			} else {
				parentUrl = jsoupResponse.url().toString();
				document = jsoupResponse.parse();
				parentPath = new URL(url).getPath();
				if (parentPath.equals("")) parentPath = "/";
				if (StaticVault.siteUrl.equals("")) StaticVault.siteUrl = parentUrl;

				pageEntity = new PageEntity(siteEntity, jsoupResponse.statusCode(), document.html(), parentPath);
			}
		} catch (IOException | UncheckedIOException exception) {
			logger.error("Can't parse JSOUP Response from URL = " + url);
			parentTask.setLastError(exception.getMessage());
			return null;
		}
		return jsoupResponse;
	}

	private void dropPageToQueue(String urlToDrop) {
		long t = System.currentTimeMillis();
		try {
			String path = new URL(urlToDrop).getPath();
//			lock.writeLock().lock();
			if (!pageRepository.existsByPathAndSiteEntity(path, siteEntity)) {
				while (true) {
					if ((queueOfPagesForSaving.remainingCapacity() < 10) && (allowed)) {
						Thread.sleep(5_000);
					} else break;
				}
				queueOfPagesForSaving.put(pageEntity);

			}
//			lock.writeLock().unlock();
		} catch (InterruptedException | MalformedURLException e) {
			throw new RuntimeException(e);
		}
		pageEntity = null;
//		System.out.println("method dropPage To Queue - " + (System.currentTimeMillis() - t) + " ms");
	}

	private void forkTasksFromSubtasks(List<ScrapingService> subTasks, Map<String, Integer> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) return;

		for (String childLink : subLinks.keySet()) {
			if (childIsValidToFork(childLink)) {
				ScrapTask childScrapTask = new ScrapTask(childLink);
				ScrapingService task = new ScrapingService(childScrapTask, siteEntity, queueOfPagesForSaving, queueOfPagesForIndexing, pageRepository, siteRepository);
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
				| ((!subLink.matches(URL_IS_FILE_LINK)) && (!subLink.contains("#")));
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
