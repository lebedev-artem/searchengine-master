package searchengine.tools.indexing;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UncheckedIOException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.Impl.IndexingServiceImpl;
import searchengine.tools.AcceptableContentTypes;
import searchengine.tools.StringPool;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import static java.lang.Thread.sleep;
import static searchengine.tools.Regex.*;

@Slf4j
@Getter
@Setter
@Component
@NoArgsConstructor
public class ScrapingAction extends RecursiveAction {

	private String siteUrl;
	private String parentUrl;
	private Document document;
	private String parentPath;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;
	private Connection.Response jsoupResponse;
	private BlockingQueue<PageEntity> outcomeQueue;
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private static final AcceptableContentTypes ACCEPTABLE_CONTENT_TYPES = new AcceptableContentTypes();

	public ScrapingAction(String parentUrl,
	                      @NotNull SiteEntity siteEntity,
	                      BlockingQueue<PageEntity> outcomeQueue) {
		this.siteEntity = siteEntity;
		this.outcomeQueue = outcomeQueue;
		this.parentUrl = parentUrl;
		this.siteUrl = IndexingActionsImpl.siteUrl;
	}

	@Override
	protected void compute() {
		List<ScrapingAction> subTasks = new LinkedList<>();

		if (pressedStop()) {
			joinTasksFromSubtasks(subTasks);
			subTasks.clear();
			return;
		}

		jsoupResponse = getResponseFromUrl(parentUrl);
		if (jsoupResponse != null) dropPageToQueue();
		else return;

		Set<String> childLinksOfTask = getChildLinks(parentUrl, document);

		if (childLinksOfTask.size() != 0) {
			forkTasksFromSubtasks(subTasks, childLinksOfTask);
			joinTasksFromSubtasks(subTasks);
		}
	}

	public Set<String> getChildLinks(String url, Document document) {
		Set<String> newChildLinks = new HashSet<>();
		if (document == null) return newChildLinks;

		Elements elements = document.select("a[href]");
		if (elements.isEmpty() || pressedStop()) return newChildLinks;

		for (Element element : elements) {
			String href = getHrefFromElement(element).toLowerCase(Locale.ROOT);

			lock.readLock().lock();
			if (StringPool.visitedLinks.containsKey(href)) continue;
			lock.readLock().unlock();

			try {
				if (url.matches(URL_IS_VALID)
						//Temp check for this site
						&& (href.startsWith(siteUrl) | href.contains("my.tretyakov.ru/app/gallery?") | href.contains("my.tretyakov.ru/app/masterpiece/"))
						&& !href.contains("#")
						&& !href.equals(url)
						&& !newChildLinks.contains(href)
						&& (HTML_EXT.stream().anyMatch(href.substring(href.length() - 4)::contains)
						| !href.matches(URL_IS_FILE_LINK))) {

					lock.readLock().lock();
					if (!StringPool.visitedLinks.containsKey(href)
							&& !StringPool.pages404.containsKey(href)
							&& !StringPool.savedPaths.containsKey(href)) {
						newChildLinks.add(href);
					}
					lock.readLock().unlock();

				}
			} catch (StringIndexOutOfBoundsException ignored) {
			}
		}
		return newChildLinks;
	}

	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {

		try {
			parentPath = new URL(url).getPath();

			lock.readLock().lock();
			if (!StringPool.visitedLinks.containsKey(url)) {
				StringPool.internVisitedLinks(url);
			} else {
				return null;
			}
			lock.readLock().unlock();

			jsoupResponse = Jsoup.connect(url).execute();
			if (!ACCEPTABLE_CONTENT_TYPES.contains(jsoupResponse.contentType())) {
				return null;
			}

			document = jsoupResponse.parse();
			pageEntity = new PageEntity(siteEntity, jsoupResponse.statusCode(), document.html(), parentPath);

			Safelist safelist = Safelist.relaxed().preserveRelativeLinks(true);
			Cleaner cleaner = new Cleaner(safelist);
			boolean isValid = cleaner.isValid(document);
			if (isValid)
				document = cleaner.clean(document);

		} catch (IOException | UncheckedIOException exception) {
			siteEntity.setLastError(exception.getMessage());
			siteEntity.setStatusTime(LocalDateTime.now());
			StringPool.internPage404(url);
			log.error("Something went wrong 404. " + url + " Pages404 vault contains " + StringPool.pages404.size() + " url");
			return null;
		}

		log.info("Response from " + url + " got successfully");
		return jsoupResponse;
	}

	private void dropPageToQueue() {
		try {
			while (true) {
				if ((outcomeQueue.remainingCapacity() < 10) && (!pressedStop())) {
					sleep(20_000);
				} else break;
			}
			lock.readLock().lock();
			if (!StringPool.savedPaths.containsKey(parentPath)) outcomeQueue.put(pageEntity);
			lock.readLock().unlock();

		} catch (InterruptedException e) {
			log.error("Cant drop page to queue");
		}
	}

	private void forkTasksFromSubtasks(List<ScrapingAction> subTasks, Set<String> subLinks) {
		if (pressedStop()) return;

		for (String childLink : subLinks) {
			if (childIsValidToFork(childLink) && !StringPool.pages404.containsKey(childLink) && !StringPool.visitedLinks.containsKey(childLink)) {
				ScrapingAction action = new ScrapingAction(childLink, siteEntity, outcomeQueue);
				action.fork();
				subTasks.add(action);
			}
		}
	}

	private void joinTasksFromSubtasks(List<ScrapingAction> childTasks) {
		if (childTasks != null)
			for (ScrapingAction task : childTasks) {
				task.join();
			}
		System.gc();
	}

	private boolean childIsValidToFork(@NotNull String subLink) {
		String ext = subLink.substring(subLink.length() - 4);
		return ((HTML_EXT.stream().anyMatch(ext::contains)) | (!subLink.matches(URL_IS_FILE_LINK)));
	}

	public String getHrefFromElement(Element element) {
		return (element != null) ? element.absUrl("href") : "";
	}

	public boolean pressedStop() {
		return IndexingServiceImpl.pressedStop;
	}

}