package searchengine.services.indexing;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.repositories.SiteEntityRepository;
import searchengine.services.utilities.ParsingEngine;
import searchengine.services.utilities.URLNameFormatter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

@AllArgsConstructor
public class ParseSiteService extends RecursiveTask<Map<String, Integer>> {
	private static final Logger logger = LogManager.getLogger(ParseSiteService.class);
	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
	private final IndexTask rootIndexTask;
	private final String zeroLevelURL;
	URLNameFormatter URLFormatter = new URLNameFormatter();
	ParsingEngine parsingEngine = new ParsingEngine();
	@Autowired
	private SiteEntityRepository siteEntityRepository;
	private final Site site;
	public static volatile boolean allowed = true;


//	public ParseSiteService(IndexTask indexTask, Site site) {
//		this.rootIndexTask = indexTask;
//		String URL = rootIndexTask.getURL();
//		zeroLevelURL = URLFormatter.createZeroLevelURL(URL);
//		parsingEngine.setZeroLevelURL(URLFormatter.createZeroLevelURL(URL));
//		this.site = site;
//	}


		public ParseSiteService(IndexTask indexTask, Site site) {
		this.rootIndexTask = indexTask;
		String URL = rootIndexTask.getURL();
		zeroLevelURL = URLFormatter.createZeroLevelURL(URL);
		parsingEngine.setZeroLevelURL(URLFormatter.createZeroLevelURL(URL));
		this.site = site;
	}



	public boolean getAllowed() {
		return allowed;
	}

	public void setAllowed(boolean allowed) {
		ParseSiteService.allowed = allowed;
	}

	@Override
	protected Map<String, Integer> compute() {
		Map<String, Integer> links = new TreeMap<>();
		String URLOfTask = rootIndexTask.getURL(); //получаем у задачи ссылку
		List<ParseSiteService> subTasks = new LinkedList<>(); //Создаем List для подзадач
		Map<String, Integer> subLinks = null; //Создаем Map для ссылок от вызвавшей ссылки

		if (!allowed) {
			extracted(links, subTasks, subLinks);
//			System.out.println("I am here parseSite line 52");
			return rootIndexTask.getLinksOfTask(); //Внешний флаг от метода indexStop
		}
		if (parsingEngine.linkIsFile(URLOfTask)) { //проверяем если ссылка - файл, то записываем в Map и возвращаем
			links.put(rootIndexTask.getURL(), URLFormatter.getLevel(rootIndexTask.getURL()));
			rootIndexTask.setLinksTask(links);
			return rootIndexTask.getLinksOfTask();
		}

		try {
			subLinks = parsingEngine.getChildLinksFromElements(URLOfTask); //Получаем все ссылки от вызвавшей ссылки
		} catch (InterruptedException ex) {
			logger.debug("Error in subLinks = getChildLinksFromElements(urlTask)");
		} catch (IOException ex) {
			logger.debug("IOException in subLinks = parsingEngine.getChildLinksFromElements(URLOfTask)");
		} catch (RuntimeException ex) {
			logger.debug("java.io.IOException: Underlying input stream returned zero byte");
		}

		extracted(links, subTasks, subLinks);
		rootIndexTask.setLinksTask(links);
		rootIndexTask.setLinksTask(subLinks);
		return rootIndexTask.getLinksOfTask();
	}

	private void extracted(Map<String, Integer> links, List<ParseSiteService> subTasks, Map<String, Integer> subLinks) {
		if (subLinks != null) {
			for (String subLink : subLinks.keySet()) {
				IndexTask subIndexTask = new IndexTask(subLink, site); //Создаем подзадачу для каждой ссылки
				ParseSiteService fork_task = new ParseSiteService(subIndexTask, site); //Рекурсия. Создаем экземпляр ParseLink для FJP от подзадачи
				fork_task.fork();
				subTasks.add(fork_task); //Добавляем задачу в список задач
				rootIndexTask.addSubTask(subIndexTask); //добавляем подзадачу в список подзадач вызвавшей задачи
			}
			for (ParseSiteService join_task : subTasks) links.putAll(join_task.join());
		}
	}
}