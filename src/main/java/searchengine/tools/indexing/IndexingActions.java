package searchengine.tools.indexing;
import searchengine.model.SiteEntity;
import java.util.Set;

public interface IndexingActions {

	void startFullIndexing(Set<SiteEntity> siteEntities);

	void startPartialIndexing(SiteEntity siteEntity);

	void setIndexingActionsStarted(boolean value);

	boolean isIndexingActionsStarted();

	void setEnabled(boolean value);
}

