package searchengine.services.parsing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexServiceImpl;
import searchengine.services.utilities.UrlFormatter;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

@Getter
@Setter
@AllArgsConstructor
public class ParseSiteService extends RecursiveTask<Map<String, Integer>> {
	private static final Logger logger = LogManager.getLogger(ParseSiteService.class);
	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
	private final ParseTask rootParseTask;
	UrlFormatter urlFormatter = new UrlFormatter();
	@Autowired
	private SiteRepository siteRepository;
	public static volatile boolean allowed = true;
	public volatile Map<String, Integer> links = new TreeMap<>();
	public HashMap<String, Integer> linksCodes = new HashMap<>();
	@Autowired
	private final IndexServiceImpl indexService;

	String urlIsFile = "http[s]?:/(?:/[^/]+){1,}/[А-Яа-яёЁ\\w ]+\\.[a-z]{3,5}(?![/]|[\\wА-Яа-яёЁ])";
	String validUrl = "^(ht|f)tp(s?)://[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(/?)([a-zA-Z0-9\\-.?,'/\\\\+&%_]*)?$";
	private String parentUrl;
	private final AcceptableContentTypes acceptableContentTypes = new AcceptableContentTypes();
	private Integer statusCode;
	private String statusMessage;
	private Connection.Response response;
	private Site site;
	int id;
	SiteEntity siteEntity;


	public ParseSiteService(ParseTask parseTask, IndexServiceImpl indexService, Site site, SiteEntity siteEntity) {
		this.rootParseTask = parseTask;
		this.indexService = indexService;
		this.site = site;
		this.siteEntity = siteEntity;
		this.id = siteEntity.getId();
		parentUrl = site.getUrl();
	}

	@Override
	protected Map<String, Integer> compute() {
		String urlOfTask = rootParseTask.getUrl(); //получаем у задачи ссылку
		List<ParseSiteService> subTasks = new LinkedList<>(); //Создаем List для подзадач
		Map<String, Integer> subTaskLinks = new TreeMap<>(); //Создаем Map для ссылок от вызвавшей ссылки

//		прерывание FJP
		if (!indexService.getAllowed()) {
			joinTasksFromSubtasks(links, subTasks);
			subTasks.clear();
			return rootParseTask.getLinksOfTask(); //Внешний флаг от метода indexStop
		}

////возможно надо перенесте в класс парсе енэине
////		сюда добвавить получание статуса
//		if (linkIsFile(urlOfTask)) { //проверяем если ссылка - файл, то записываем в Map и возвращаем
//			links.put(rootParseTask.getUrl(), urlFormatter.getLevel(rootParseTask.getUrl()));
//			rootParseTask.setLinksTask(links);
//			return rootParseTask.getLinksOfTask();
//		}

		try {
			if (!indexService.getLinks().containsKey(urlOfTask)) {
				subTaskLinks = getSubLinks(urlOfTask); //Получаем все ссылки от вызвавшей ссылки
			}
//			logger.warn("get sublinks from " + urlOfTask );
		} catch (InterruptedException ex) {
			logger.debug("Error in subLinks = getChildLinksFromElements(urlTask)");
		} catch (IOException ex) {
			logger.debug("IOException in subLinks = getChildLinksFromElements(URLOfTask)");
		} catch (RuntimeException ex) {
			ex.printStackTrace();
			logger.debug("java.io.IOException: Underlying input stream returned zero byte");
		}

		forkTasksFromSubtasks(subTasks, subTaskLinks);
		joinTasksFromSubtasks(links, subTasks);

		rootParseTask.setLinksTask(links);
		rootParseTask.setLinksTask(subTaskLinks);
		return rootParseTask.getLinksOfTask();
	}

	public Map<String, Integer> getSubLinks(String url) throws InterruptedException, IOException {
		Map<String, Integer> subLinks = new HashMap<>();
		siteRepository = indexService.getSiteRepository();
		String content;
		String path;
		response = getResponseFromUrl(url);
		if (response == null) return subLinks;

		Document document = response.parse();
		Elements elements = document.select("a[href]");
		content = getCleanedBody(document);
		path = new URL(url).getPath();
		PageEntity pageEntity = new PageEntity(siteEntity, path, statusCode, content);

//		эти две строки надо вынести отдельно, когда получили ссылку, и сразу отправили
		if (!indexService.getLinks().containsKey(url)) {
//			logger.warn("indexService.getLinks().put(" + url + ")");
			indexService.getLinks().put(url, statusCode); //добавляем ссылку с кодом в мап
		}
		if (!indexService.getPages().contains(pageEntity)) {
//			logger.warn("indexService.getPages().add(" + pageEntity.getPath() + ")");
			indexService.getPages().add(pageEntity); //добавляем пэдж в сет
//			indexService.getPageRepository().save(pageEntity);
		}

		if (elements.isEmpty()) return subLinks;

//		Если ссылка - файл, сразу в таблицу
		if (linkIsFile(url)) {
			links.put(rootParseTask.getUrl(), statusCode);
			subLinks.put(url, statusCode);
			return subLinks;
		}

		try {
			for (Element element : elements) {
				String href = urlFormatter.getHref(element);
				String cleanHref = urlFormatter.cleanHref(href);

				if (href.matches(validUrl)
						&& href.contains(urlFormatter.cleanHref(parentUrl))
						&& !cleanHref.equals(urlFormatter.cleanHref(url))
						&& (cleanHref.indexOf(urlFormatter.cleanHref(url)) == 0)
						&& !subLinks.containsKey(href)) {
					subLinks.put(href, statusCode);
				}
			}
		} catch (NullPointerException ex) {
			logger.error(" Error in " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Elements from URL is empty\n");
		}
		return subLinks;
	}


	@ConfigurationProperties(prefix = "jsoup-setting")
	private Connection.Response getResponseFromUrl(String url) {
		try {
			response = Jsoup.connect(url).execute();
			statusCode = response.statusCode();
			statusMessage = response.statusMessage();
		} catch (IOException exception) {
			logger.error("IOException in " + Thread.currentThread().getStackTrace()[1].getMethodName() + ". " + exception);
			return null;
		}

		if (!acceptableContentTypes.contains(response.contentType())) {
			logger.error("connectionResponse = Jsoup.connect(url).execute(). Not acceptable content type");
			logger.warn(response.url() + response.contentType() + response.statusCode() + response.statusMessage());
			return null;
		}
		return response;
	}


	private @NotNull String getCleanedBody(Document document) {
		return Jsoup.clean(String.valueOf(document), Safelist.relaxed());
	}

	private void forkTasksFromSubtasks(List<ParseSiteService> subTasks, Map<String, Integer> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) {
			return;
		}
		for (String subLink : subLinks.keySet()) {
			ParseTask subParseTask = new ParseTask(subLink); //Создаем подзадачу для каждой ссылки
			ParseSiteService task = new ParseSiteService(subParseTask, indexService, site, siteEntity); //Рекурсия. Создаем экземпляр ParseLink для FJP от подзадачи
			task.fork();//форкаем
			subTasks.add(task); //Добавляем задачу в список задач
			rootParseTask.addSubTask(subParseTask, subLinks.get(subLink)); //добавляем подзадачу в список подзадач вызвавшей задачи
		}
	}

	private static void joinTasksFromSubtasks(Map<String, Integer> links, List<ParseSiteService> subTasks) {
		if (subTasks != null) for (ParseSiteService task : subTasks) links.putAll(task.join());
		else {
			logger.error("Error while joining tasks on method <" + Thread.currentThread().getStackTrace()[1].getMethodName() + "> ");
		}
	}

	public void setAllowed(boolean allowed) {
		ParseSiteService.allowed = allowed;
	}

	public boolean linkIsFile(@NotNull String link) {
		return link.matches(urlIsFile);
	}
}
