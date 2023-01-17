package searchengine.services.indexing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import searchengine.services.utilities.ParsingEngine;
import searchengine.services.utilities.URLNameFormatter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

public class ParseSite extends RecursiveTask<Map<String, Integer>> {
	private static final Logger logger = LogManager.getRootLogger();
	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
	private final Task rootTask;
	private final String zeroLevelURL;
	URLNameFormatter URLFormatter = new URLNameFormatter();
	ParsingEngine parsingEngine = new ParsingEngine();

	public ParseSite(Task t) {
		this.rootTask = t;
		String URL = rootTask.getURL();
		zeroLevelURL = URLFormatter.createZeroLevelURL(URL);
		parsingEngine.setZeroLevelURL(URLFormatter.createZeroLevelURL(URL));
	}

	@Override
	protected Map<String, Integer> compute() {
		Map<String, Integer> links = new TreeMap<>();
		String URLOfTask = rootTask.getURL(); //получаем у задачи ссылку

		if (parsingEngine.linkIsFile(URLOfTask)) { //проверяем если ссылка - файл, то записываем в Map и возвращаем
			links.put(rootTask.getURL(), URLFormatter.getLevel(rootTask.getURL()));
			rootTask.setLinksTask(links);
			return rootTask.getLinksOfTask();
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
				Task subTask = new Task(subLink); //Создаем подзадачу для каждой ссылки
				ParseSite fork_task = new ParseSite(subTask); //Рекурсия. Создаем экземпляр ParseLink для FJP от подзадачи
				fork_task.fork();
				subTasks.add(fork_task); //Добавляем задачу в список задач
				rootTask.addSubTask(subTask); //добавляем подзадачу в список подзадач вызвавшей задачи
			}
			for (ParseSite join_task : subTasks) links.putAll(join_task.join());
		}
		rootTask.setLinksTask(links);
		rootTask.setLinksTask(subLinks);
		return rootTask.getLinksOfTask();
	}
}
