package searchengine.services.indexing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import searchengine.config.Site;
import searchengine.repositories.SiteRepository;
import searchengine.services.utilities.ParsingEngine;
import searchengine.services.utilities.URLNameFormatter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

public class ParseSite extends RecursiveTask<Map<String, Integer>> {
	private static final Logger logger = LogManager.getLogger(ParseSite.class);
	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
	private final IndexTask rootIndexTask;
	private final String zeroLevelURL;
	URLNameFormatter URLFormatter = new URLNameFormatter();
	ParsingEngine parsingEngine = new ParsingEngine();
	@Autowired
	private SiteRepository siteRepository;
	@Autowired
	private Site site;

	public ParseSite(IndexTask t, Site site) {
		this.rootIndexTask = t;
		String URL = rootIndexTask.getURL();
		zeroLevelURL = URLFormatter.createZeroLevelURL(URL);
		parsingEngine.setZeroLevelURL(URLFormatter.createZeroLevelURL(URL));
		this.site = site;
	}

	@Override
	protected Map<String, Integer> compute() {
		Map<String, Integer> links = new TreeMap<>();
		String URLOfTask = rootIndexTask.getURL(); //получаем у задачи ссылку

		if (parsingEngine.linkIsFile(URLOfTask)) { //проверяем если ссылка - файл, то записываем в Map и возвращаем
			links.put(rootIndexTask.getURL(), URLFormatter.getLevel(rootIndexTask.getURL()));
			rootIndexTask.setLinksTask(links);
			return rootIndexTask.getLinksOfTask();
		}

		List<ParseSite> subTasks = new LinkedList<>(); //Создаем List для подзадач
		Map<String, Integer> subLinks = null; //Создаем Map для ссылок от вызвавшей ссылки
		try {
			subLinks = parsingEngine.getChildLinksFromElements(URLOfTask); //Получаем все ссылки от вызвавшей ссылки
		} catch (InterruptedException ex) {
			logger.debug("Error in subLinks = getChildLinksFromElements(urlTask)");
		} catch (IOException ex) {
			logger.debug("IOException in subLinks = parsingEngine.getChildLinksFromElements(URLOfTask)");
		} catch (RuntimeException ex) {
			logger.debug("java.io.IOException: Underlying input stream returned zero byte");
		}

		if (subLinks != null) {
			for (String subLink : subLinks.keySet()) {
				IndexTask subIndexTask = new IndexTask(subLink, site); //Создаем подзадачу для каждой ссылки
				ParseSite fork_task = new ParseSite(subIndexTask, site); //Рекурсия. Создаем экземпляр ParseLink для FJP от подзадачи
				fork_task.fork();
				subTasks.add(fork_task); //Добавляем задачу в список задач
				rootIndexTask.addSubTask(subIndexTask); //добавляем подзадачу в список подзадач вызвавшей задачи
			}
			for (ParseSite join_task : subTasks) links.putAll(join_task.join());
		}
		rootIndexTask.setLinksTask(links);
		rootIndexTask.setLinksTask(subLinks);
		return rootIndexTask.getLinksOfTask();
	}
}
