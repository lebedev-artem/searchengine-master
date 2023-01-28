package searchengine.services.utilities;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
import searchengine.services.interfaces.FillEntity;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class FillEntityImpl implements FillEntity {

	private SiteEntity siteEntity = new SiteEntity();

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