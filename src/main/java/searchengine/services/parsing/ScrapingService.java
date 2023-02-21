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
import org.jsoup.UncheckedIOException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.context.properties.ConfigurationProperties;
import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusIndexing;
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
public class ScrapingService extends RecursiveTask<Integer> {
	private static final Logger logger = LogManager.getLogger(ScrapingService.class);
	public static volatile boolean allowed = true;
	private final IndexServiceImpl indexService;
	private final AcceptableContentTypes acceptableContentTypes = new AcceptableContentTypes();
	private final ScrapTask parentTask;
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private Integer pagesCountOfTask = 0;

	private Connection.Response jsoupResponse;
	private Document document;

	private List<String> htmlExt = new ArrayList<>() {{
		add("html");
		add("dhtml");
		add("shtml");
		add("xhtml");
	}};

	private String regexProtocol = "^(http|https)://(www.)?";
	private String regexUrlIsFileLink = "https?:/(?:/[^/]+)+/[А-Яа-яёЁ\\w ]+\\.[a-z]{3,5}(?!/|[\\wА-Яа-яёЁ])";
	private String regexUrlIsValid = "^(ht|f)tp(s?)://[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(/?)([a-zA-Z0-9\\-.?,'/\\\\+&%_]*)?$";

	private Integer parentStatusCode;
	private String parentStatusMessage;
	private String parentContent;
	private String parentPath;
	private String parentUrl;
	private Site site;
	private Integer siteId;
	private SiteEntity siteEntity;
	private Map<String, String> l;


	public ScrapingService(ScrapTask scrapTask, IndexServiceImpl indexService, @NotNull Site site, SiteEntity siteEntity) {
		this.parentTask = scrapTask;
		this.indexService = indexService;
		this.site = site;
		parentUrl = site.getUrl();
		this.siteEntity = siteEntity;
		this.siteId = siteEntity.getId();
//		l = indexService.getStringPool().getLinks();
	}

	@Override
	protected Integer compute() {
		String urlOfTask = parentTask.getUrl(); //получаем у задачи ссылку
		List<ScrapingService> subTasks = new LinkedList<>(); //Создаем List для подзадач

		if (!indexService.isAllowed()) {
			joinTasksFromSubtasks(subTasks);
			subTasks.clear();
			return parentTask.getPagesCountOfTask();
		}

		Connection.Response responseSinglePage = getResponseFromUrl(urlOfTask);
		if (responseSinglePage != null) {
			checkAndSavePage();
		} else return parentTask.getPagesCountOfTask();

		if (jsoupResponse == null)
			jsoupResponse = getResponseFromUrl(urlOfTask);

		Map<String, Integer> childLinksOfTask = getChildLinks(urlOfTask, document);

		forkTasksFromSubtasks(subTasks, childLinksOfTask);
		joinTasksFromSubtasks(subTasks);

		parentTask.setPagesCountOfTask(pagesCountOfTask);
		parentTask.setLinks(childLinksOfTask);

		return parentTask.getPagesCountOfTask();
	}

	public synchronized Map<String, Integer> getChildLinks(String url, Document document) {
		Map<String, Integer> newChildLinks = new HashMap<>();
		if (document == null) return newChildLinks;
		Elements elements = document.select("a[href]");
		if (urlIsFile(url) || elements.isEmpty()) return newChildLinks;

		for (Element element : elements) {
			String href = getHrefFromElement(element);
//			String cleanHref = cleanFromProtocolAndSlash(href);

			try {
				if (url.matches(regexUrlIsValid) && href.startsWith(parentUrl) && !newChildLinks.containsKey(href) && !href.equals(url)) {
					if (((htmlExt.stream().anyMatch(href.substring(href.length() - 4)::contains) || !href.matches(regexUrlIsFileLink)))) {
						String elementPath = null;
						try {
							elementPath = new URL(href).getPath();
						} catch (MalformedURLException e) {
							logger.warn("MalformedURLException");
						}
						synchronized (indexService.getPageRepository()) {
							if (indexService.getPageRepository().existsByPath(elementPath)) {
								continue;
							}
						}

						newChildLinks.put(href, parentStatusCode);
					}
				}
			} catch (StringIndexOutOfBoundsException e) {
				System.out.println("12");
			}

		}
//		logger.warn("return " + newChildLinks.size() + " links from " + url);
		return newChildLinks;
	}

	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.@Nullable Response getResponseFromUrl(String url) {
		try {
//			logger.warn("get response from " + url);
			jsoupResponse = Jsoup.connect(url).execute();
			parentUrl = jsoupResponse.url().toString();
//			jsoupResponse.bufferUp();
		} catch (IOException | UncheckedIOException exception) {
//			logger.error("Cant execute jsoup.Connect " + Thread.currentThread().getStackTrace()[1].getMethodName() + ". ex - " + exception + " url - " + url + " parentUrl - " + parentUrl + " parentTaskUrl - " + parentTask.getUrl());
			parentTask.setLastError(exception.getMessage());
			return null;
		}

		if (!acceptableContentTypes.contains(jsoupResponse.contentType())) {
//			logger.warn(jsoupResponse.url() + jsoupResponse.contentType() + jsoupResponse.statusCode() + jsoupResponse.statusMessage());
			return null;
		} else {
			try {
				document = jsoupResponse.parse();
				parentPath = new URL(url).getPath();
				if (parentPath.equals("")) parentPath = "/";
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

		lock.writeLock().lock();
		String tempParentPath = parentPath;

		synchronized (TempStorage.class) {
			if (TempStorage.pages.stream().noneMatch(x -> x.getPath().equals(tempParentPath)))
				if (!indexService.getPageRepository().existsByPath(parentPath)) {
//					PageEntity pageEntity = ;
//					indexService.getPageRepository().saveAndFlush(pageEntity);
					lock.readLock().lock();
					TempStorage.pages.add(new PageEntity(siteEntity, parentStatusCode, parentContent, parentPath));
					lock.readLock().unlock();
					TempStorage.count++;
					lock.writeLock().unlock();
					if (TempStorage.count % 50 == 0) {
						System.out.println("| ");
					} else {
						System.out.print("| ");
					}
				}
		}

		synchronized (TempStorage.class) {
			if (TempStorage.count == 100) {
				TempStorage.count = 0;
				logger.info(TempStorage.count + " pages of " + site.getName() + " saved to DB");
				lock.writeLock().lock();
				logger.warn("start save");
				Set<PageEntity> buff = new HashSet<>(TempStorage.pages);
				indexService.getPageRepository().saveAll(buff);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				lock.readLock().lock();
				TempStorage.pages.clear();
				lock.readLock().unlock();
				lock.writeLock().unlock();
				System.gc();
			}
		}


//		lock.writeLock().lock();
//		if (!indexService.getPageRepository().existsByPath(parentPath)) {
//			lock.readLock().lock();
//			indexService.getPageRepository().saveAndFlush(pageEntity);
//			logger.info("parentUrl - " + parentUrl + "; parenPath - " + parentPath + "; parenTask - " + parentTask.toString());
//			lock.readLock().unlock();
//		}
//		lock.writeLock().unlock();


//
//		TempStorage.pages.add(pageEntity);
//		pagesCountOfTask++;
//
//		lock.writeLock().lock();
//		lock.readLock().lock();
//		if (TempStorage.pages.size() == 100){
//			logger.info(TempStorage.pages.size() + " pages of " + site.getName() + " saved to DB");
//			Set<PageEntity> buff = new HashSet<>(TempStorage.pages);
//			indexService.getPageRepository().saveAllAndFlush(buff);
//			TempStorage.pages.clear();
//			lock.readLock().unlock();
//			lock.writeLock().unlock();
//		}
//		System.gc();

//		lock.writeLock().lock();
//		lock.readLock().lock();
//		try {
//			if (!TempStorage.paths.contains(pageEntity.getPath())) {
////				TempStorage.paths.add(pageEntity.getPath());
////				TempStorage.pages.add(pageEntity);
//				paths.add(parentPath);
//				pages.add(pageEntity);
//				pagesCountOfTask = pagesCountOfTask + 1;
//				System.out.print(TempStorage.pages.size() + " |");
//				if ((TempStorage.pages.size() % 400 == 0) && (TempStorage.pages.size() != 0)) {
//					countPages = countPages + 400;
//					System.out.println(countPages + " pages of " + site.getUrl() + " saved to DB | ");
////					logger.info(countPages + " pages of " + site.getName() + " saved to DB");
//					Set<PageEntity> buff = new HashSet<>(TempStorage.pages);
//					indexService.getPageRepository().saveAllAndFlush(buff);
//					TempStorage.pages.clear();
////					TempStorage.paths.clear();
//					System.gc();
//				}
//			}
//		} catch (Exception e) {
//			logger.warn("Cant save entity to DB -> " + Thread.currentThread().getStackTrace()[1].getMethodName());
//		} finally {
//			lock.writeLock().unlock();
//			lock.readLock().unlock();
//		}
	}

	private void forkTasksFromSubtasks(List<ScrapingService> subTasks, Map<String, Integer> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) return;

		for (String subLink : subLinks.keySet()) {
			if ((htmlExt.stream().noneMatch(subLink.substring(subLink.length() - 4)::contains)) && (!subLink.matches(regexUrlIsFileLink)) && (!subLink.contains("#"))) {
				ScrapTask childScrapTask = new ScrapTask(subLink);
				ScrapingService task = new ScrapingService(childScrapTask, indexService, site, siteEntity);
				task.fork();
				subTasks.add(task); //убрать, зачем собирать задачи
				parentTask.addChildTask(childScrapTask);
			}
		}
		System.gc();
	}

	private void joinTasksFromSubtasks(List<ScrapingService> childTasks) {
		if (childTasks != null) for (ScrapingService task : childTasks)
			pagesCountOfTask = pagesCountOfTask + task.join();
		else {
			logger.error("Error while joining tasks on method. childTasks is null -> " + Thread.currentThread().getStackTrace()[1].getMethodName());
		}
	}

	public void setAllowed(boolean allowed) {
		ScrapingService.allowed = allowed;
	}

	//htmlExt.stream().anyMatch(url.substring(url.length() - 4)::contains);
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
}
