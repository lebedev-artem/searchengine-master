package searchengine.services.savingpages;

import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.concurrent.BlockingQueue;

public interface SavingPagesService {
	void savePages();

	void setScrapingIsDone(boolean b);

	void setPressedStop(boolean b);

	void setIncomeQueue(BlockingQueue<PageEntity> incomeQueue);

	void setOutcomeQueue(BlockingQueue<PageEntity> outcomeQueue);

	void setSiteEntity(SiteEntity siteEntity);
}


