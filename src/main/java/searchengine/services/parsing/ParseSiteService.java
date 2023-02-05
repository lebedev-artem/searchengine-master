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
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.StatusIndexing;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexServiceImpl;
import searchengine.services.utilities.UrlFormatter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;

@Getter
@Setter
@AllArgsConstructor
public class ParseSiteService extends RecursiveTask<Map<String, Integer>> {
	private static final Logger logger = LogManager.getLogger(ParseSiteService.class);
	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
	private final ParseTask rootParseTask;
	private final String zeroLevelURL;
	UrlFormatter urlFormatter = new UrlFormatter();
	@Autowired
	private SiteRepository siteRepository;
	public static volatile boolean allowed = true;
	public volatile Map<String, Integer> links = new TreeMap<>();
	public HashMap<String, Integer> linksCodes = new HashMap<>();
	@Autowired
	private final IndexServiceImpl indexService;

	String regexLinkIsFile = "http[s]?:/(?:/[^/]+){1,}/[А-Яа-яёЁ\\w ]+\\.[a-z]{3,5}(?![/]|[\\wА-Яа-яёЁ])";
	String regexValidURL = "^(ht|f)tp(s?)://[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(/?)([a-zA-Z0-9\\-.?,'/\\\\+&%_]*)?$";
	private String zeroLevelUrl;
	private final AcceptableContentTypes acceptableContentTypes = new AcceptableContentTypes();
	Integer statusCode;
	private Connection.Response connectionResponse;

	public ParseSiteService(ParseTask parseTask, IndexServiceImpl indexService) {
		this.rootParseTask = parseTask;
		this.indexService = indexService;
		String taskUrl = rootParseTask.getUrl();
		zeroLevelURL = urlFormatter.createZeroLevelUrl(taskUrl);
		setZeroLevelUrl(urlFormatter.createZeroLevelUrl(taskUrl));
	}

	@Override
	protected Map<String, Integer> compute() {
		String urlOfTask = rootParseTask.getUrl(); //получаем у задачи ссылку
		List<ParseSiteService> subTasks = new LinkedList<>(); //Создаем List для подзадач
		Map<String, Integer> subTaskLinks = new TreeMap<>(); //Создаем Map для ссылок от вызвавшей ссылки

//		прерывание FJP
		if (!allowed) {
			joinTasksFromSubtasks(links, subTasks);
			subTasks.clear();
			return rootParseTask.getLinksOfTask(); //Внешний флаг от метода indexStop
		}


//возможно надо перенесте в класс парсе енэине
		if (linkIsFile(urlOfTask)) { //проверяем если ссылка - файл, то записываем в Map и возвращаем
			links.put(rootParseTask.getUrl(), urlFormatter.getLevel(rootParseTask.getUrl()));
			rootParseTask.setLinksTask(links);
			return rootParseTask.getLinksOfTask();
		}

		try {
			subTaskLinks = getSubLinks(urlOfTask); //Получаем все ссылки от вызвавшей ссылки
		} catch (InterruptedException ex) {
			logger.debug("Error in subLinks = getChildLinksFromElements(urlTask)");
		} catch (IOException ex) {
			logger.debug("IOException in subLinks = getChildLinksFromElements(URLOfTask)");
		} catch (RuntimeException ex) {
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
		Elements elements = new Elements();
		Document document;
		connectionResponse = getResponseFromUrl(url);

		try {
			elements = connectionResponse.parse().select("a[href]");
			statusCode = connectionResponse.statusCode();
			indexService.getMainLinks().put(url, statusCode);
			indexService.getPages().add(new PageEntity(new SiteEntity("INDEXING", LocalDateTime.now(), "", url, "name"), url, statusCode, Jsoup.connect(url).get().body().html()));

			if (elements.size() == 0) return subLinks;

			for (Element element : elements) {
				String s = urlFormatter.extractLink(element);
				String cleanS = urlFormatter.cleanUrl(s);

				if (s.matches(regexValidURL) //Проверяем валидность сслыки
						&& s.contains(urlFormatter.cleanUrl(zeroLevelUrl)) //Ссылка того же домена как и домен вызвавшей
						&& !cleanS.equals(urlFormatter.cleanUrl(url)) //Ссылка не равна вызвавшей ссылке, loop
						&& cleanS.indexOf(urlFormatter.cleanUrl(url)) == 0
						//Ссылка того же уровня как вызвавшая, тоже устраняет loop
						//как проверять итоговую коллекцию на предмет наличия ссылки еще не додумал
						//под ТЗ подходит. Ссылки не уходят на уровень вниз, т.е. строят дерево вперед, и за стартовую страницу
						//берут тот уровень ссылки, которая на входе
						&& !subLinks.containsKey(s)) //Ссылка еще не добавлена во временную Map
				{
					if (s.matches(regexLinkIsFile)) {
						subLinks.put(s, statusCode);
//                            System.out.println("Added " + s);
					} else {
						subLinks.put(s, statusCode);
//                            System.out.println("Added " + s);
					}
				}
			}

//			else {
//				System.out.println(formatter.format(date) + " Elements object is null from " + url);
//			}
		} catch (NullPointerException ex) {
//            ex.printStackTrace();
			System.out.println(formatter.format(new Date()) + " Error in <getChildLinksFromElements>. Elements from URL is empty\n");
		}
//		if (subLinks.size() > 0) {
//			System.out.println(formatter.format(date) + " " + Thread.currentThread().getName() + ", parsed " + subLinks.size() + " links <- " + url);
//		}
		return subLinks;
	}


	@ConfigurationProperties(prefix = "jsoup-setting")
	private @Nullable Connection.Response getResponseFromUrl(String url) throws IOException, UnknownHostException, SocketTimeoutException, UnsupportedMimeTypeException {
		connectionResponse = Jsoup.connect(url).execute();

		if (!acceptableContentTypes.contains(connectionResponse.contentType())) {
			logger.error("connectionResponse = Jsoup.connect(url).execute(). Not acceptable content type");
			logger.warn(connectionResponse.url());
			return null;
		}
		return connectionResponse;
	}

	public void setZeroLevelUrl(String zeroLevelUrl) {
		this.zeroLevelUrl = zeroLevelUrl;
	}


	private void forkTasksFromSubtasks(List<ParseSiteService> subTasks, Map<String, Integer> subLinks) {
		if ((subLinks == null) || subLinks.isEmpty()) {
			return;
		}
		for (String subLink : subLinks.keySet()) {
			ParseTask subParseTask = new ParseTask(subLink); //Создаем подзадачу для каждой ссылки
			ParseSiteService task = new ParseSiteService(subParseTask, indexService); //Рекурсия. Создаем экземпляр ParseLink для FJP от подзадачи
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

	private static void getStatusCode(String url) {
		HttpURLConnection cont = null;
		try {
			cont = (HttpURLConnection) new URL(url).openConnection();
			cont.setRequestMethod("HEAD");
			cont.connect();
			int statusCode = cont.getResponseCode();
		} catch (IOException e) {
			logger.error("Impossible get Status Code");
		}
	}

	public boolean linkIsFile(@NotNull String link) {
		return link.matches(regexLinkIsFile);
	}
}
