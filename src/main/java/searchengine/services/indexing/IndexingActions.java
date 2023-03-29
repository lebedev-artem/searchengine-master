package searchengine.services.indexing;

import searchengine.model.SiteEntity;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;

public interface IndexingActions {

	void startFullIndexing(Set<SiteEntity> siteEntities);
	void startPartialIndexing(SiteEntity siteEntity);
	boolean getIndexingActionsStarted();
	void setIndexingActionsStarted(boolean value);


}

