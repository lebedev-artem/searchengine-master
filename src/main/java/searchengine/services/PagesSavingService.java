package searchengine.services;

import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.tools.StringPool;

import java.util.concurrent.BlockingQueue;

public interface PagesSavingService {
	void startSavingPages();
	void setScrapingIsDone(boolean b);
	void setIncomeQueue(BlockingQueue<PageEntity> incomeQueue);
	void setOutcomeQueue(BlockingQueue<Integer> outcomeQueue);
	void setSiteEntity(SiteEntity siteEntity);
	void setStringPool(StringPool stringPool);
}


