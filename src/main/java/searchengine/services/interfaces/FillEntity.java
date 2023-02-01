package searchengine.services.interfaces;

import searchengine.config.Site;
import searchengine.model.SiteEntity;
import java.util.List;

public interface FillEntity {
	List<SiteEntity> initSiteEntity();

	SiteEntity initSiteEntity(Site site);
}
