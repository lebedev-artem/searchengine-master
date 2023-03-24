package searchengine.services.indexing;

import searchengine.model.SiteEntity;

import java.util.Set;

public interface IndexingActions {

	void startFullIndexing(Set<SiteEntity> siteEntities);
	void startPartialIndexing();
	void setPressedStop(boolean value);
	boolean getIndexingActionsStarted();
	void setIndexingActionsStarted(boolean value);
}
