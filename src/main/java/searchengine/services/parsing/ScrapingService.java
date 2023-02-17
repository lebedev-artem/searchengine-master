package searchengine.services.parsing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.context.properties.ConfigurationProperties;
import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.indexing.IndexServiceImpl;
import searchengine.services.utilities.UrlFormatter;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Getter
@Setter
@AllArgsConstructor
public class ScrapingService extends RecursiveTask<Map<String, Integer>> {
	private static final Logger logger = LogManager.getLogger(ScrapingService.class);
	public static int countPages = 0;
	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
	private static volatile Set<String> pathCheck = new HashSet<>();
	private final ScrapTask parentTask;
	private String parentUrl;
	private UrlFormatter urlFormatter = new UrlFormatter();
	public static volatile boolean allowed = true;
	public volatile HashMap<String, Integer> links = new HashMap<>();
	private final IndexServiceImpl indexService;
	private Connection.Response jsoupResponse;
	private Document document;
	ReadWriteLock lock = new ReentrantReadWriteLock();


	String regexUrlIsFileLink = "http[s]?:/(?:/[^/]+){1,}/[А-Яа-яёЁ\\w ]+\\.[a-z]{3,5}(?![/]|[\\wА-Яа-яёЁ])";
	String regexUrlIsValid = "^(ht|f)tp(s?)://[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(/?)([a-zA-Z0-9\\-.?,'/\\\\+&%_]*)?$";

	private final AcceptableContentTypes acceptableContentTypes = new AcceptableContentTypes();
	private Integer parentStatusCode;
	private String parentStatusMessage;
	private String parentContent;
	private String parentPath;
	private Site site;
	int id;
	Integer siteId;
	SiteEntity siteEntity;
	long heapSize = Runtime.getRuntime().totalMemory();
	long heapMaxSize = Runtime.getRuntime().maxMemory();
	long heapFreeSize = Runtime.getRuntime().freeMemory();


	public ScrapingService(ScrapTask scrapTask, IndexServiceImpl indexService, Site site, Integer siteId, SiteEntity siteEntity) {
		this.parentTask = scrapTask;
		this.indexService = indexService;
		this.site = site;
		this.siteId = siteId;
		parentUrl = site.getUrl();
		this.siteEntity = siteEntity;
	}

	@Override
	protected Map<String, Integer> compute() {
		String urlOfTask = parentTask.getUrl(); //получаем у задачи ссылку
		List<ScrapingService> subTasks = new LinkedList<>(); //Создаем List для подзадач

//		synchronized (TempStorage.class) {
//			if ((TempStorage.pages.size() % 100 == 0) & TempStorage.pages.size() > 0) {
//				logger.warn(TempStorage.pages.size() + " pages in storage");
//				indexService.storeInDatabase(TempStorage.pages);
//				logger.warn("~ Try store in DB");
//				TempStorage.pageEntityHashMap.keySet().clear();
//			}
//		}

//		прерывание FJP, Внешний флаг от метода indexStop
		if (!indexService.isAllowed()) {
			joinTasksFromSubtasks(links, subTasks);
			subTasks.clear();
			return parentTask.getLinks();
		}

		//		Если ссылки нет, то записали

		//				logger.warn(urlOfTask + " saved to TempStorage.threadSafeUniqueUrls");
		lock.readLock().lock();
		try {
			TempStorage.urls.add(urlOfTask);
		} finally {
			lock.readLock().unlock();
		}


////		Если ссылки нет, то записали
//		synchronized (this) {
//			if (!TempStorage.urls.contains(urlOfTask)) {
////				logger.warn(urlOfTask + " saved to TempStorage.threadSafeUniqueUrls");
//				TempStorage.urls.add(urlOfTask);
//			}
//		}
//				Сохраним страницу если отдаст все параметры
		Connection.Response responseSinglePage = getResponseFromUrl(urlOfTask);
		if (responseSinglePage != null) {
			checkAndSavePage(urlOfTask);
//				Получаем все ссылки от вызвавшей ссылки
		} else return parentTask.getLinks();

		if (jsoupResponse == null)
			jsoupResponse = getResponseFromUrl(urlOfTask);
		//Создаем Map для ссылок от вызвавшей ссылки
		HashMap<String, Integer> childLinksOfTask = getChildLinks(urlOfTask, document);

		forkTasksFromSubtasks(subTasks, childLinksOfTask);
		joinTasksFromSubtasks(links, subTasks);

		parentTask.setLinks(links);
		parentTask.setLinks(childLinksOfTask);
		return parentTask.getLinks();
	}

	private void checkAndSavePage(String url) {
		PageEntity pageEntity = createPageEntityFromUrl(url);
		lock.writeLock().lock();
		lock.readLock().lock();
		try {
			if (!TempStorage.paths.contains(pageEntity.getPath())) {
				TempStorage.pages.add(pageEntity);
//			logger.info(TempStorage.pages.size() + " pages in Set -> " + pageEntity.getPath());
				if (TempStorage.pages.size() % 50 == 0) {
					countPages = countPages + 50;
					logger.info(countPages + " pages of " + site.getName() + " in Set");
					Set<PageEntity> buff = new HashSet<>(TempStorage.pages);
					indexService.getPageRepository().saveAll(buff);
					TempStorage.pages.clear();
				}
			}
		} finally {
			lock.writeLock().unlock();
			lock.readLock().unlock();
		}


//			indexService.getPageRepository().save(pageEntity);
//			logger.warn("indexService.getPageRepository().save(" + pageEntity.getPath() + ") saved in DB.");
	}

	public synchronized HashMap<String, Integer> getChildLinks(String url, Document document) {
		HashMap<String, Integer> subLinks = new HashMap<>();
		if (document == null) return subLinks;

		Elements elements = document.select("a[href]");
		if (urlIsFile(url) || elements.isEmpty()) return subLinks;

		for (Element element : elements) {
			String href = urlFormatter.getHref(element);
			String cleanHref = urlFormatter.cleanedHref(href);
			if (href.contains("7047afbf4d327b7471ed252ac19a7c13.JPG"))
				System.out.println("warn");
//			synchronized (this) {
			if (urlIsValid(href)
					&& href.contains(urlFormatter.cleanedHref(parentUrl))
					&& href.startsWith(parentUrl)
					&& !cleanHref.equals(urlFormatter.cleanedHref(url))
					&& !subLinks.containsKey(href)
					&& !href.contains("#")
					&& !href.contains("mailto:")
					&& !href.contains("@")
					&& !TempStorage.urls.contains(href) && (!urlIsFile(href) || urlIsHtmlFile(href))) {
				lock.writeLock().lock();
				try {
					TempStorage.urls.add(href);
				} finally {
					lock.writeLock().unlock();
				}


//						logger.warn(href + " html page added to links");
				subLinks.put(href, href.length());
			}
		}
//		}
		return subLinks;
	}

	//Получаем response, code, status
	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {
		try {
			jsoupResponse = Jsoup.connect(url).execute();
			jsoupResponse.bufferUp();
		} catch (IOException exception) {
//			logger.error("IOException in " + Thread.currentThread().getStackTrace()[1].getMethodName() + ". ex - " + exception + " url - " + url + " parentUrl - " + parentUrl + " parentTaskUrl - " + parentTask.getUrl());
			parentTask.setLastError(exception.getMessage());
			return null;
		}

		if (!acceptableContentTypes.contains(jsoupResponse.contentType())) {
//			logger.error("connectionResponse = Jsoup.connect(url).execute(). Not acceptable content type");
			logger.warn(jsoupResponse.url() + jsoupResponse.contentType() + jsoupResponse.statusCode() + jsoupResponse.statusMessage());
			return null;
		} else {
			try {
				document = jsoupResponse.parse();
				parentPath = new URL(url).getPath();
			} catch (IOException e) {
				return null;
			}
			parentContent = document.html();
			parentStatusCode = jsoupResponse.statusCode();
			parentStatusMessage = jsoupResponse.statusMessage();
		}
		return jsoupResponse;
	}

	private void forkTasksFromSubtasks(List<ScrapingService> subTasks, HashMap<String, Integer> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) return;

		for (String subLink : subLinks.keySet()) {
			synchronized (TempStorage.class) {

				ScrapTask childScrapTask = new ScrapTask(subLink);
				ScrapingService task = new ScrapingService(childScrapTask, indexService, site, siteId, siteEntity); //Рекурсия. Создаем экземпляр ParseLink для FJP от подзадачи
				task.fork();
				subTasks.add(task);
				parentTask.addChildTask(childScrapTask);
			}
		}
	}

	private void joinTasksFromSubtasks(HashMap<String, Integer> links, List<ScrapingService> subTasks) {
		if (subTasks != null) for (ScrapingService task : subTasks) links.putAll(task.join());
//		else {
//			logger.error("Error while joining tasks on method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> ");
//		}
	}

	public void setAllowed(boolean allowed) {
		ScrapingService.allowed = allowed;
	}

	public boolean urlIsHtmlFile(@NotNull String url) {
		String endsWith = url.substring(url.length() - 4);
		return endsWith.equalsIgnoreCase("html") || endsWith.equalsIgnoreCase("dhtml") || endsWith.equalsIgnoreCase("xhtml") || endsWith.equalsIgnoreCase("shtml");

	}

	private boolean urlIsFile(@NotNull String link) {
		return link.matches(regexUrlIsFileLink);
	}

	//					&& (cleanHref.indexOf(urlFormatter.cleanHref(url)) == 0)
	private boolean conditionsForUrl(String url, HashMap<String, Integer> subLinks, String href, String cleanHref) {
		boolean result
				= urlIsValid(href)
				&& href.contains(urlFormatter.cleanedHref(parentUrl))
				&& !cleanHref.equals(urlFormatter.cleanedHref(url))
				&& !subLinks.containsKey(href)
				&& !href.contains("#")
				&& !href.contains("mailto:")
				&& !href.contains("@");
		return result;
	}

	private boolean urlIsValid(String url) {
		return url.matches(regexUrlIsValid);
	}


	private PageEntity createPageEntityFromUrl(String url) {
		return new PageEntity(siteEntity, parentStatusCode, parentContent, parentPath);
	}

	public boolean linkIsValid(String url) {
		try {
			new URL(url).toURI();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
