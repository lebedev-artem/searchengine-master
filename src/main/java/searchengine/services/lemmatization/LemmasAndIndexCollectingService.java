package searchengine.services.lemmatization;

import lombok.Getter;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.services.indexing.IndexServiceImpl;

import java.util.concurrent.BlockingQueue;

public interface LemmasAndIndexCollectingService {

	void startCollecting();
	LemmaEntity createLemmaEntity(String lemma);
	Boolean allowed();
	LemmaEntity increaseLemmaFrequency(String lemma);

	void setIncomeQueue(BlockingQueue<Integer> queueOfPagesForLemmasCollecting);
	void setSavingPagesIsDone(boolean b);
	void setSiteEntity(SiteEntity siteEntity);
	boolean pressedStop();
}



