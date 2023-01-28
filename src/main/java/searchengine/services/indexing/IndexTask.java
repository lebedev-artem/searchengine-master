package searchengine.services.indexing;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import searchengine.config.Site;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

@Getter
@Setter
@RequiredArgsConstructor
public class IndexTask {
	private final String URL;
	private final ArrayList<IndexTask> subIndexTasks = new ArrayList<>();
	private int level = 0;
	private final Map<String, Integer> links = new TreeMap<>();
	private final Site site;

	public void addSubTask(IndexTask indexTask) {
		indexTask.setLevel(level + 1);
		subIndexTasks.add(indexTask);
	}

	public Map<String, Integer> getLinksOfTask() {
		return links;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public void setLinksTask(Map<String, Integer> linksOfTask) {
		this.links.putAll(linksOfTask);
	}
}
