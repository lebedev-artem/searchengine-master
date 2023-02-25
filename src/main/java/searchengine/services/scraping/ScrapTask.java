package searchengine.services.scraping;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class ScrapTask {
	private final String url;
	private String lastError;
	private ArrayList<ScrapTask> childTasks = new ArrayList<>();

	public ScrapTask(String url) {
		this.url = url;
	}

	public void addChildTask(ScrapTask scrapTask) {
		childTasks.add(scrapTask);
	}
}

