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
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.tools.AcceptableContentTypes;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Thread.sleep;
import static searchengine.tools.Regex.*;
import static searchengine.tools.StringPool.*;

@Slf4j
@Getter
@Setter
@Component
@NoArgsConstructor
public class ScrapingAction extends RecursiveAction {

	public static volatile Boolean enabled = true;
	private String siteUrl;
	private String parentUrl;
	private Document document;
	private String parentPath;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;
	private Set<String> childLinksOfTask;
	private Connection.Response jsoupResponse = null;
	private BlockingQueue<PageEntity> outcomeQueue;
	private Environment environment;
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private static final AcceptableContentTypes ACCEPTABLE_CONTENT_TYPES = new AcceptableContentTypes();

	public ScrapingAction(String parentUrl,
						  @NotNull SiteEntity siteEntity,
						  BlockingQueue<PageEntity> outcomeQueue, Environment environment) {
		this.siteEntity = siteEntity;
		this.outcomeQueue = outcomeQueue;
		this.parentUrl = parentUrl;
		this.environment = environment;
		this.siteUrl = IndexingActionsImpl.siteUrl;
	}

	@Override
	protected void compute() {
		if (!enabled)
			return;

		jsoupResponse = getResponseFromUrl(parentUrl);
		if (jsoupResponse != null) {
			spoolPageToQueue();

			final Elements elements = document.select("a[href]");
			if (!elements.isEmpty()) {
				childLinksOfTask = getChildLinks(parentUrl, elements);
			}

			if (childLinksOfTask != null) forkAndJoinTasks();
		}
	}

	public Set<String> getChildLinks(String url, Elements elements) {
		Set<String> newChildLinks = new HashSet<>();

		for (Element element : elements) {
			final String href = getHrefFromElement(element).toLowerCase(Locale.ROOT);

			lock.readLock().lock();
			if (visitedLinks.containsKey(href))
				continue;
			else if (urlIsValidToProcess(url, newChildLinks, href)) {
				addHrefToOutcomeValue(newChildLinks, href);
			}
			lock.readLock().unlock();
		}
		return newChildLinks;
	}

	private void addHrefToOutcomeValue(Set<String> newChildLinks, String href) {
		if (!visitedLinks.containsKey(href)
				&& !pages404.containsKey(href)
				&& !savedPaths.containsKey(href)) {
			newChildLinks.add(href);
		}
	}

	private boolean urlIsValidToProcess(String sourceUrl, Set<String> newChildLinks, String extractedHref) {
		return sourceUrl.matches(URL_IS_VALID)
				&& extractedHref.startsWith(siteUrl)
				&& !extractedHref.contains("#")
				&& !extractedHref.equals(sourceUrl)
				&& !newChildLinks.contains(extractedHref)
				&&
				(HTML_EXT.stream().anyMatch(extractedHref.substring(extractedHref.length() - 4)::contains)
						| !extractedHref.matches(URL_IS_FILE_LINK));
	}

	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {

		lock.readLock().lock();
		if (!visitedLinks.containsKey(url)) internVisitedLinks(url);
		else
			return null;
		lock.readLock().unlock();

		try {
			jsoupResponse = Jsoup.connect(url).execute();
			if (ACCEPTABLE_CONTENT_TYPES.contains(jsoupResponse.contentType())) {
				document = jsoupResponse.parse();
				parentPath = new URL(url).getPath();
				cleanHtmlContent();
				pageEntity = new PageEntity(siteEntity, jsoupResponse.statusCode(), document.html(), parentPath);
			} else
				return null;
		} catch (IOException | UncheckedIOException exception) {
			urlNotAvailableActions(url, exception);
			return null;
		}
		if (Objects.equals(environment.getProperty("user-settings.logging-enable"), "true")){
			log.info("Response from " + url + " got successfully");
		}

		return jsoupResponse;
	}

	private void urlNotAvailableActions(String url, @NotNull Exception exception) {
		siteEntity.setLastError(exception.getMessage());
		siteEntity.setStatusTime(LocalDateTime.now());
		internPage404(url);
		if (Objects.equals(environment.getProperty("user-settings.logging-enable"), "true")){
			log.error("Something went wrong 404. " + url + " Pages404 vault contains " + pages404.size() + " url");
		}

	}

	private void cleanHtmlContent() {
		final String oldTitle = document.title();
		final Safelist safelist = Safelist.relaxed().preserveRelativeLinks(true);
		final Cleaner cleaner = new Cleaner(safelist);
		boolean isValid = cleaner.isValid(document);
		if (!isValid) {
			document = cleaner.clean(document);
			document.title(oldTitle);
		}
	}

	private void spoolPageToQueue() {
		try {
			while (true) {
				if (outcomeQueue.remainingCapacity() < 10 && enabled) {
					sleep(20_000);
				} else break;
			}
			lock.readLock().lock();
			if (!savedPaths.containsKey(parentPath))
				outcomeQueue.put(pageEntity);
			lock.readLock().unlock();

		} catch (InterruptedException e) {
			log.error("Cant drop page to queue");
		}
	}

	private void forkAndJoinTasks() {
		if (!enabled)
			return;

		List<ScrapingAction> subTasks = new LinkedList<>();

		for (String childLink : childLinksOfTask) {
			if (childIsValidToFork(childLink)
					&& !pages404.containsKey(childLink)
					&& !visitedLinks.containsKey(childLink)) {
				ScrapingAction action = new ScrapingAction(childLink, siteEntity, outcomeQueue, environment);
				action.fork();
				subTasks.add(action);
			}
		}

		for (ScrapingAction task : subTasks) task.join();
	}

	private boolean childIsValidToFork(@NotNull String subLink) {
		final String ext = subLink.substring(subLink.length() - 4);
		return (HTML_EXT.stream().anyMatch(ext::contains) | !subLink.matches(URL_IS_FILE_LINK));
	}

	public String getHrefFromElement(Element element) {
		return (element != null) ? element.absUrl("href") : "";
	}

}
