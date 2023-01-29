package searchengine.services.interfaces;

import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.SiteEntity;

import java.util.List;

@Service
public interface FillEntity {
	List<SiteEntity> initSiteEntity();

	SiteEntity initSiteEntity(Site site);
}
