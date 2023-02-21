package searchengine.services.parsing;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
//@RequiredArgsConstructor
public class ScrapTask {
	private final String url;
	private String lastError;
	private volatile Integer pagesCountOfTask = 0;
//	private PageEntity pageOfTask;
//	private SiteEntity siteOfTask;
	private Map<String, Integer> links = new HashMap<>();
//	private ArrayList<PageEntity> childPagesOfTask = new ArrayList<>();
	private ArrayList<ScrapTask> childTasks = new ArrayList<>();

	public ScrapTask(String url) {
		this.url = url;
	}
//
//	public void createPage(SiteEntity siteEntity, int statusCode, String content, String path) {
//		pageOfTask = new PageEntity(siteEntity, statusCode, content, path);
//	}

	public void addChildTask(ScrapTask scrapTask) {
		childTasks.add(scrapTask);
	}

	public void addCountOfPages(Integer newCount){
		pagesCountOfTask = pagesCountOfTask + newCount;
	}
}


//	public void setSiteOfTask(SiteEntity siteOfTask) {
//		this.siteOfTask = siteOfTask;
//	}

//	public PageEntity getPageOfTask() {
//		return pageOfTask;
//	}

//	public Map<String, Integer> getLinksOfTask() {
//		return links;
//	}

//	public void setLinksTask(HashMap<String, Integer> linksOfTask) {
//		this.links.putAll(linksOfTask);
//	}