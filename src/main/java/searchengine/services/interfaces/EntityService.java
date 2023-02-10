package searchengine.services.interfaces;

import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import java.util.List;

public interface EntityService {
	List<SiteEntity> initSiteTable(SitesList sitesList);

	SiteEntity initSiteTable(Site site);

	void deleteAllPagesByPath(String path);

}
