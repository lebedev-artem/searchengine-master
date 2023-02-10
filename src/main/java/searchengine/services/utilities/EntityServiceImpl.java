package searchengine.services.utilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.services.interfaces.EntityService;
import searchengine.services.interfaces.IndexService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



@Service
public class EntityServiceImpl implements EntityService {
	@Autowired
	private final SitesList sitesList;
	@Autowired
	PageRepository pageRepository;
	private static final Logger logger = LogManager.getLogger(EntityServiceImpl.class);

	public EntityServiceImpl(SitesList sitesList) {
		this.sitesList = sitesList;
	}

	@Override
	public List<SiteEntity> initSiteTable(SitesList sitesList) {
		List<SiteEntity> siteEntities = new ArrayList<>();
		for (Site site : sitesList.getSites()) siteEntities.add(initSiteTable(site));
		return siteEntities;
	}

	@Override
	public SiteEntity initSiteTable(Site site){
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setStatus("INDEXING");
		siteEntity.setStatusTime(LocalDateTime.now());
		siteEntity.setLastError("");
		siteEntity.setUrl(site.getUrl());
		siteEntity.setName(site.getName());
		return siteEntity;
	}

	@Override
	public void deleteAllPagesByPath(String path) {
		List<PageEntity> pagesToDel = pageRepository.findAll();
		int count = 0;
		for (PageEntity page : pagesToDel) {
			if (page.getPath().contains(path)){
				pageRepository.delete(page);
				logger.warn("~ " + page.getId() + " " + page.getPath() + " deleted");
				count++;
			}
		}
		logger.warn(count + " pages deleted from search_engine.page");
	}


}