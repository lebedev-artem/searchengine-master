package searchengine.services.utilities;

import searchengine.config.Site;
import searchengine.config.SitesList;

import java.util.List;

public class SingleSiteListCreator {
	public SitesList getSiteList(String url, Site site) {
		Site singleSite = new Site();
		singleSite.setUrl(url);
		singleSite.setName(site.getName());
		SitesList singleSiteList = new SitesList();
		singleSiteList.setSites(List.of(singleSite));
		return singleSiteList;
	}
}
