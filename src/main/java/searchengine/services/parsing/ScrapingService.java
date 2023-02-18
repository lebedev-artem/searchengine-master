package searchengine.services.parsing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
//	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
//	private static volatile Set<String> pathCheck = new HashSet<>();
	private final ScrapTask parentTask;
	private String parentUrl;
//	private UrlFormatter urlFormatter = new UrlFormatter();
	public static volatile boolean allowed = true;
	public volatile Map<String, Integer> links = new HashMap<>();
	private final IndexServiceImpl indexService;
	private Connection.Response jsoupResponse;
	private Document document;
	private ReadWriteLock lock = new ReentrantReadWriteLock();

	private List<String> htmlExt = new ArrayList<>() {{
		add("html");
		add("dhtml");
		add("shtml");
		add("xhtml");
	}};
	private String regexProtocol = "^(http|https)://(www.)?";
	private String regexUrlIsFileLink = "https?:/(?:/[^/]+)+/[А-Яа-яёЁ\\w ]+\\.[a-z]{3,5}(?!/|[\\wА-Яа-яёЁ])";
	private String regexUrlIsValid = "^(ht|f)tp(s?)://[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(/?)([a-zA-Z0-9\\-.?,'/\\\\+&%_]*)?$";

	private final AcceptableContentTypes acceptableContentTypes = new AcceptableContentTypes();
	private Integer parentStatusCode;
	private String parentStatusMessage;
	private String parentContent;
	private String parentPath;
	private Site site;
//	int id;
	Integer siteId;
	SiteEntity siteEntity;
//	long heapSize = Runtime.getRuntime().totalMemory();
//	long heapMaxSize = Runtime.getRuntime().maxMemory();
//	long heapFreeSize = Runtime.getRuntime().freeMemory();


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

//		прерывание FJP, Внешний флаг от метода indexStop
		if (!indexService.isAllowed()) {
			joinTasksFromSubtasks(links, subTasks);
			subTasks.clear();
			return parentTask.getLinks();
		}

////		возможно это надо убрать
//		lock.readLock().lock();
//		try {
//			TempStorage.urls.add(urlOfTask);
//		} finally {
//			lock.readLock().unlock();
//		}

		Connection.Response responseSinglePage = getResponseFromUrl(urlOfTask);
		if (responseSinglePage != null) {
			checkAndSavePage();
		} else return parentTask.getLinks();

		if (jsoupResponse == null)
			jsoupResponse = getResponseFromUrl(urlOfTask);

		Map<String, Integer> childLinksOfTask = getChildLinks(urlOfTask, document);

		forkTasksFromSubtasks(subTasks, childLinksOfTask);
		joinTasksFromSubtasks(links, subTasks);

		parentTask.setLinks(links);
		parentTask.setLinks(childLinksOfTask);
		return parentTask.getLinks();
	}

	public synchronized Map<String, Integer> getChildLinks(String url, Document document) {
		Map<String, Integer> newChildLinks = new HashMap<>();
		if (document == null) return newChildLinks;

		Elements elements = document.select("a[href]");
		if (urlIsFile(url) || elements.isEmpty()) return newChildLinks;

		for (Element element : elements) {
			String href = getHrefFromElement(element);
			String cleanHref = cleanFromProtocolAndSlash(href);
			String tempPath;

			if (cleanHref.contains("pdf") && cleanHref.contains(parentUrl)){
				System.out.println("pdf");
			}

			try {
				tempPath = new URL(href).getPath();
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
			lock.writeLock().lock();
			if (urlIsValid(href)
					&& href.startsWith(parentUrl)
//					&& href.contains(cleanFromProtocolAndSlash(parentUrl))
					&& !cleanHref.equals(cleanFromProtocolAndSlash(url))
					&& !newChildLinks.containsKey(href)
//					&& (cleanHref.indexOf(cleanFromProtocolAndSlash(url)) == 0) //Только ссылки от первого уровня
					&& (!urlIsFile(href) || urlIsHtmlFile(href)) //этот возможно надо убрать, оставить только НЕ ФАЙЛ
					&& !TempStorage.paths.contains(tempPath))
//					!TempStorage.urls.contains(href))
			{
				try {
					lock.readLock().lock();
					TempStorage.paths.add(tempPath);
//					TempStorage.urls.add(href);
				} finally {
					lock.writeLock().unlock();
					lock.readLock().unlock();
				}
				newChildLinks.put(href, href.length());
			}
		}
		return newChildLinks;
	}

	//Получаем response, code, status
	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {
		try {
			jsoupResponse = Jsoup.connect(url).execute();
			jsoupResponse.bufferUp();
		} catch (IOException exception) {
//			logger.error("Cant execute jsoup.Connect " + Thread.currentThread().getStackTrace()[1].getMethodName() + ". ex - " + exception + " url - " + url + " parentUrl - " + parentUrl + " parentTaskUrl - " + parentTask.getUrl());
			parentTask.setLastError(exception.getMessage());
			return null;
		}

		if (!acceptableContentTypes.contains(jsoupResponse.contentType())) {
			logger.warn(jsoupResponse.url() + jsoupResponse.contentType() + jsoupResponse.statusCode() + jsoupResponse.statusMessage());
			return null;
		} else {
			try {
				document = jsoupResponse.parse();
				parentPath = new URL(url).getPath();
			} catch (IOException e) {
				logger.warn("Cant parse jsoupResponse to Document -> " + Thread.currentThread().getStackTrace()[1].getMethodName());
				return null;
			}
			parentContent = document.html();
			parentStatusCode = jsoupResponse.statusCode();
			parentStatusMessage = jsoupResponse.statusMessage();
		}
		return jsoupResponse;
	}

	private void checkAndSavePage() {
		PageEntity pageEntity = new PageEntity(siteEntity, parentStatusCode, parentContent, parentPath);
		lock.writeLock().lock();
		lock.readLock().lock();
		try {
			if (!TempStorage.paths.contains(pageEntity.getPath())) {
				TempStorage.pages.add(pageEntity);
				if (TempStorage.pages.size() % 50 == 0) {
					countPages = countPages + 50;
//					logger.info(countPages + " pages of " + site.getName() + " in Set");
					Set<PageEntity> buff = new HashSet<>(TempStorage.pages);
					indexService.getPageRepository().saveAll(buff);
					TempStorage.pages.clear();
					TempStorage.paths.clear();
				}
			}
		} catch (Exception e) {
			logger.warn("Cant save entity to DB -> " + Thread.currentThread().getStackTrace()[1].getMethodName());
		} finally {
			lock.writeLock().unlock();
			lock.readLock().unlock();
		}
		if (heapIsExceeded()){
			logger.info(indexService.getPageRepository().findAll().size() + " pages in DB");
			logger.info("pages size - " + TempStorage.pages.size() + " urls size - " + TempStorage.urls.size() + " path size - " + TempStorage.paths.size());
			logger.info("Force perform GC");
			System.gc();
		}
	}

	private void forkTasksFromSubtasks(List<ScrapingService> subTasks, Map<String, Integer> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) return;

		for (String subLink : subLinks.keySet()) {
			ScrapTask childScrapTask = new ScrapTask(subLink);
			ScrapingService task = new ScrapingService(childScrapTask, indexService, site, siteId, siteEntity); //Рекурсия. Создаем экземпляр ParseLink для FJP от подзадачи
			task.fork();
			subTasks.add(task);
			parentTask.addChildTask(childScrapTask);
		}
	}

	private void joinTasksFromSubtasks(Map<String, Integer> links, List<ScrapingService> childTasks) {
		if (childTasks != null) for (ScrapingService task : childTasks) links.putAll(task.join());
		else {
			logger.error("Error while joining tasks on method. childTasks is null -> " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}
	}

	public void setAllowed(boolean allowed) {
		ScrapingService.allowed = allowed;
	}

	public boolean urlIsHtmlFile(@NotNull String url) {
		String ext = url.substring(url.length() - 4);
		return htmlExt.stream().anyMatch(ext::contains);
	}

	private boolean urlIsFile(@NotNull String link) {
		return link.matches(regexUrlIsFileLink);
	}

	@Contract(pure = true)
	private boolean urlIsValid(@NotNull String url) {
		return url.matches(regexUrlIsValid);
	}

	public String getHrefFromElement(Element element) {
		return (element != null) ? element.absUrl("href") : "";
	}

	private String cleanFromProtocolAndSlash(@NotNull String dirtyHref) {
		String cleanHref = dirtyHref.replaceAll(regexProtocol, "");
		return (!cleanHref.endsWith("/")) ? cleanHref.replace("/", "") : cleanHref;
	}

	private boolean heapIsExceeded(){
		return Runtime.getRuntime().totalMemory() % 536870912 == 0;
	}
}
