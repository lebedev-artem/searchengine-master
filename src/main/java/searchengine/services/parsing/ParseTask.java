package searchengine.services.parsing;
import lombok.Getter;
import lombok.Setter;
import searchengine.model.PageEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Getter
@Setter
//@RequiredArgsConstructor
public class ParseTask {
	private final String url;
	private int statusCode;
	private final ArrayList<ParseTask> subTasks = new ArrayList<>();
	private final Map<String, Integer> links = new TreeMap<>();
	private final Map<Integer, PageEntity> pages = new HashMap<>();


	public ParseTask(String url) {
		this.url = url;
	}

	public void addSubTask(ParseTask parseTask, Integer statusCode) {
		subTasks.add(parseTask);
		this.statusCode = statusCode;
	}

	public Map<String, Integer> getLinksOfTask() {
		return links;
	}

	public void setLinksTask(Map<String, Integer> linksOfTask) {
		this.links.putAll(linksOfTask);
	}
	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}
}
