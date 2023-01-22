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
import searchengine.services.ShutdownService;
import searchengine.services.utilities.FillEntity;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Transactional
@Component
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService{
	@Autowired
	private SiteRepository siteRepository;
	@Autowired
	private FillEntity fillEntity;
	private static final Logger logger = LogManager.getRootLogger();
	private ForkJoinPool pool = new ForkJoinPool();

	public ForkJoinPool getPool() {
		return pool;
	}

	@Override
	public synchronized boolean indexingStart(Site site)  {
		siteRepository.deleteByName(site.getName());
		logger.info("Info about " + site.getName() + " was added to search_engine.site");
		siteRepository.save(fillEntity.fillSiteEntity(Status.INDEXING, "", site.getUrl(), site.getName()));
		logger.info("Start indexing " + site.getName() + " " + site.getUrl());
		Task rootTask = new Task(site.getUrl());
		ParseSite parseSite = new ParseSite(rootTask);
		pool.invoke(parseSite);
		logger.info(rootTask.getLinksOfTask().size() + " links was parsed from " + rootTask.getURL());
		logger.warn("Status of " + site.getName() + " was changed to INDEXED");
		siteRepository.changeSiteStatus("INDEXED", site.getName());
		return true;
	}

	@Override
	public void indexingStop() {
		ShutdownService service = new ShutdownService();
		service.stop(pool);
	}
}
