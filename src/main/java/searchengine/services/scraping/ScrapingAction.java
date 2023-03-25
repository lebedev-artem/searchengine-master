package searchengine.services.scraping;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UncheckedIOException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexServiceImpl;
import searchengine.services.indexing.IndexingActions;
import searchengine.services.indexing.IndexingActionsImpl;
import searchengine.services.stuff.AcceptableContentTypes;
import searchengine.services.stuff.StaticVault;
import searchengine.services.stuff.StringPool;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Thread.sleep;
import static searchengine.services.stuff.Regex.*;

@Slf4j
@Getter
@Setter
@Service
@NoArgsConstructor
//@RequiredArgsConstructor
public class ScrapingAction extends RecursiveAction {
	private static final Integer COUNT_PAGES_TO_DROP = 50;
	private static final AcceptableContentTypes ACCEPTABLE_CONTENT_TYPES = new AcceptableContentTypes();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	long timeStart;

	private Connection.Response jsoupResponse;
	private Document document;

	private Integer parentStatusCode;
	private String parentStatusMessage;
	private String parentContent;
	private String parentPath;
	private String parentUrl;
	private String siteUrl;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;

	private BlockingQueue<PageEntity> outcomeQueue;
	private PageRepository pageRepository;
	private SiteRepository siteRepository;
	private StringPool stringPool;

	public ScrapingAction(String parentUrl,
	                      @NotNull SiteEntity siteEntity,
	                      BlockingQueue<PageEntity> outcomeQueue,
	                      PageRepository pageRepository,
	                      SiteRepository siteRepository,
	                      StringPool stringPool) {
		this.siteEntity = siteEntity;
		this.outcomeQueue = outcomeQueue;
		this.parentUrl = parentUrl;
		this.pageRepository = pageRepository;
		this.siteRepository = siteRepository;
		this.siteUrl = IndexingActionsImpl.siteUrl;
		this.stringPool = stringPool;
	}

	@Override
	protected void compute() {
		timeStart = System.currentTimeMillis();
		List<ScrapingAction> subTasks = new LinkedList<>();

		if (pressedStop()) {
			joinTasksFromSubtasks(subTasks);
			subTasks.clear();
			return;
		}

		Connection.Response responseSinglePage = getResponseFromUrl(parentUrl);
		if (responseSinglePage != null) {
			dropPageToQueue();
		} else return;

		if (jsoupResponse == null)
			jsoupResponse = getResponseFromUrl(parentUrl);

		Set<String> childLinksOfTask = getChildLinks(parentUrl, document);

		forkTasksFromSubtasks(subTasks, childLinksOfTask);
		joinTasksFromSubtasks(subTasks);
		System.gc();
	}

	public synchronized Set<String> getChildLinks(String url, Document document) {
		Set<String> newChildLinks = new HashSet<>();
		if (document == null) return newChildLinks;
		Elements elements = document.select("a[href]");
		if (elements.isEmpty()) return newChildLinks;
		if (pressedStop()) return newChildLinks;

		for (Element element : elements) {
			String href = getHrefFromElement(element).toLowerCase(Locale.ROOT);
			try {
//				if (pageRepository.existsByPathAndSiteEntity(new URL(href).getPath(), siteEntity)) continue;

				//можно добавить проверку чтоб на уровень вниз не уходить, цикличность
				if (url.matches(URL_IS_VALID)
						&& href.startsWith(siteUrl)
						&& !href.contains("#")
						&& !href.equals(url)
						&& !newChildLinks.contains(href)
						&& !stringPool.pages404.containsKey(href)
						&& (HTML_EXT.stream().anyMatch(href.substring(href.length() - 4)::contains)
						| !href.matches(URL_IS_FILE_LINK))) {
//					if
//					{

//						synchronized (StringPool.class) {
					//Здесь можно еще добавить проверку по репозиторию
					lock.writeLock().lock();
					if (!pageRepository.existsByPathAndSiteEntity(new URL(href).getPath(), siteEntity)) {
						newChildLinks.add(href);
					}
					lock.writeLock().unlock();
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
		return newChildLinks;
	}

	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {

		if (stringPool.pages404.containsKey(url))
			return null;

		try {
			parentPath = new URL(url).getPath();
			lock.writeLock().lock();
			if (pageRepository.existsByPathAndSiteEntity(parentPath, siteEntity))
				return null;
			lock.writeLock().unlock();

			jsoupResponse = Jsoup.connect(url).execute();
			if (!ACCEPTABLE_CONTENT_TYPES.contains(jsoupResponse.contentType())) {
				return null;
			}

			document = jsoupResponse.parse();
			pageEntity = new PageEntity(siteEntity, jsoupResponse.statusCode(), document.html(), parentPath);

		} catch (IOException | UncheckedIOException exception) {
			log.error("Can't parse JSOUP Response from URL = " + url);
			siteRepository.updateErrorStatusTimeByUrl(exception.getMessage(), LocalDateTime.now(), siteEntity.getUrl());
			stringPool.internPage404(url);
			return null;
		}
		return jsoupResponse;
	}

	private void dropPageToQueue() {
		try {
			while (true) {
				if ((outcomeQueue.remainingCapacity() < 10) && (!pressedStop())) {
					sleep(20_000);
				} else break;
			}

			lock.writeLock().lock();
			if (!pageRepository.existsByPathAndSiteEntity(pageEntity.getPath(), siteEntity))
				outcomeQueue.put(pageEntity);
			lock.writeLock().unlock();

		} catch (InterruptedException e) {
			log.error("Cant drop page to queue");
		}
		pageEntity = null;
	}

	private void forkTasksFromSubtasks(List<ScrapingAction> subTasks, Set<String> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) return;
		if (pressedStop()) return;

		for (String childLink : subLinks) {
			if (childIsValidToFork(childLink)) {
				ScrapingAction task = new ScrapingAction(childLink, siteEntity, outcomeQueue, pageRepository, siteRepository, stringPool);
				task.fork();
				subTasks.add(task);
			}
		}
	}

	private void joinTasksFromSubtasks(List<ScrapingAction> childTasks) {
		if (childTasks != null)
			for (ScrapingAction task : childTasks) task.join();
	}

	private static boolean childIsValidToFork(@NotNull String subLink) {
		return (HTML_EXT.stream().anyMatch(subLink.substring(subLink.length() - 4)::contains))
				| (!subLink.matches(URL_IS_FILE_LINK));
	}

	public String getHrefFromElement(Element element) {
		return (element != null) ? element.absUrl("href") : "";
	}

	public boolean pressedStop() {
		return IndexServiceImpl.pressedStop;
	}
}
