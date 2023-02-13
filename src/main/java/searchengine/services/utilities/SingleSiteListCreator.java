package searchengine.services.utilities;

import lombok.RequiredArgsConstructor;
import searchengine.config.Site;
import searchengine.config.SitesList;

import java.util.List;

@RequiredArgsConstructor
public class SingleSiteListCreator {
private final SitesList sitesList;

	public SitesList getSiteList(String url, String hostName) {
		Site site = sitesList.getSites().stream()
				.filter(s -> hostName.equals(s.getUrl()))
				.findAny()
				.orElse(null);
		if (site == null) return null;

		Site singleSite = new Site();
		singleSite.setUrl(url);
		singleSite.setName(site.getName());
		SitesList singleSiteList = new SitesList();
		singleSiteList.setSites(List.of(singleSite));
		return singleSiteList;
	}

}
