package searchengine.services;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.concurrent.BlockingQueue;

public interface LemmasAndIndexCollectingService {

	void setIncomeQueue(BlockingQueue<Integer> queueOfPagesForLemmasCollecting);
	void setSiteEntity(SiteEntity siteEntity);
	void setSavingPagesIsDone(boolean b);
	void startCollecting();
	boolean pressedStop();
	LemmaEntity createLemmaEntity(String lemma);
	Boolean allowed();
}



