package searchengine.services.utilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.services.interfaces.FillEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FillEntityImpl implements FillEntity {
	@Autowired
	private final SitesList sitesList;

	private SiteEntity siteEntity = new SiteEntity();

	public FillEntityImpl(SitesList sitesList) {
		this.sitesList = sitesList;
	}

	@Override
	public List<SiteEntity> initSiteEntity() {
		List<SiteEntity> siteEntities = new ArrayList<>();
		for (Site site : sitesList.getSites()) {
			siteEntities.add(initSiteEntity(site));
		};
		return siteEntities;
	}

	@Override
	public SiteEntity initSiteEntity(Site site){
		siteEntity = new SiteEntity();
		siteEntity.setStatus("INDEXING");
		siteEntity.setStatusTime(LocalDateTime.now());
		siteEntity.setLastError("");
		siteEntity.setUrl(site.getUrl());
		siteEntity.setName(site.getName());
		return siteEntity;
	}

}