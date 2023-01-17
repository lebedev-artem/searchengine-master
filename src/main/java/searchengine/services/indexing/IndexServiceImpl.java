package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;
import searchengine.services.utilities.FillEntity;

import java.util.concurrent.ForkJoinPool;

@Transactional
@Component
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService{

	@Autowired
	private SiteRepository siteRepository;
	@Autowired
	private FillEntity fillEntity;
	private static final Logger logger = LogManager.getRootLogger();

	@Override
	public void indexingStart(Site site) {
		Task rootTask = new Task(site.getUrl());
		ParseSite parseSite = new ParseSite(rootTask);
		logger.info("Info about " + site.getName() + " was added to search_engine.site");
		siteRepository.save(fillEntity.fillSiteEntity(Status.INDEXING, "", site.getUrl(), site.getName()));
		siteRepository.flush();
		ForkJoinPool pool = new ForkJoinPool();
		pool.invoke(parseSite);
		logger.info(rootTask.getLinksOfTask().size() + " links was parsed from " + rootTask.getURL());

	}

	@Override
	public void indexingStop() {
		System.out.printf("stop");
	}
}
