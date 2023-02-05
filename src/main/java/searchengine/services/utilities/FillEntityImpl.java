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

	public FillEntityImpl(SitesList sitesList) {
		this.sitesList = sitesList;
	}

	@Override
	public List<SiteEntity> initSiteTable() {
		List<SiteEntity> siteEntities = new ArrayList<>();
		for (Site site : sitesList.getSites()) {
			siteEntities.add(initSiteTable(site));
		};
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

}