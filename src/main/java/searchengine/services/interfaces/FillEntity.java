package searchengine.services.interfaces;

import searchengine.config.Site;
import searchengine.model.SiteEntity;
import java.util.List;

public interface FillEntity {
	List<SiteEntity> initSiteTable();

	SiteEntity initSiteTable(Site site);
}
