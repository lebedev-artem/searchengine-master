package searchengine.services.savingpages;

import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.stuff.StringPool;

import java.util.concurrent.BlockingQueue;

public interface PagesSavingService {
	void startSavingPages();
	void setScrapingIsDone(boolean b);
	void setIncomeQueue(BlockingQueue<PageEntity> incomeQueue);
	void setOutcomeQueue(BlockingQueue<Integer> outcomeQueue);
	void setSiteEntity(SiteEntity siteEntity);
	void setStringPool(StringPool stringPool);
}


