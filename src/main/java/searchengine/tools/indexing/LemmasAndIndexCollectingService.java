package searchengine.tools.indexing;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.concurrent.BlockingQueue;

public interface LemmasAndIndexCollectingService {

	void setIncomeQueue(BlockingQueue<PageEntity> queueOfPagesForLemmasCollecting);
	void setSiteEntity(SiteEntity siteEntity);
//	void setSavingPagesIsDone(boolean b);
	void startCollecting();
	void setEnabled(boolean value);
	LemmaEntity createLemmaEntity(String lemma);
	Boolean allowed();
	void setScrapingIsDone(boolean b);
}



