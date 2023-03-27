package searchengine.services.savingpages;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.services.indexing.IndexServiceImpl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Thread.sleep;

@Slf4j
@Getter
@Setter
@Service
@RequiredArgsConstructor
public class PagesSavingServiceImpl implements PagesSavingService {
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private volatile boolean scrapingIsDone;                                     //previous step of indexing
	private BlockingQueue<PageEntity> incomeQueue;                      //from scraping
	private BlockingQueue<Integer> outcomeQueue;                        //to lemmas and index collecting
	private SiteEntity siteEntity;
	private PageEntity pageEntity;
	private final PageRepository pageRepository;

	public void startSavingPages() {
		scrapingIsDone = false;                                         //нужно устанавливать в потоке scraping
		final long startTime = System.currentTimeMillis();

		while (allowed()) {
			if (pressedStop()) {
				incomeQueue.clear();
				outcomeQueue.clear();
				return;
			}

			pageEntity = incomeQueue.poll();
			if (pageEntity != null) {
				if (!pageRepository.existsByPathAndSiteEntity(pageEntity.getPath(), siteEntity)) {
					lock.readLock().lock();
					pageRepository.save(pageEntity);
					lock.readLock().unlock();
					tryPutPageIdToOutcomeQueue();
					log.warn(pageEntity.getId() + " saved. queue has " + incomeQueue.size());
				}
			} else {
				try {
					sleep(1_000);
				} catch (InterruptedException e) {
					log.error("Can't sleep after getting null pageEntity");
				}
			}
		}
		log.info(logAboutEachSite(startTime));
	}


	private @NotNull Boolean allowed() {
		return !scrapingIsDone | incomeQueue.iterator().hasNext();
	}

	private @NotNull String logAboutEachSite(long startTime) {
		return pageRepository.countBySiteEntity(siteEntity)
				+ " pages saved in DB from site url " + siteEntity.getUrl()
				+ " in " + (System.currentTimeMillis() - startTime) + " ms";
	}

	private void tryPutPageIdToOutcomeQueue() {
		try {
			while (true) {
				if (outcomeQueue.remainingCapacity() < 5 && !pressedStop()) sleep(5_000);
				else break;
			}
			outcomeQueue.put(pageEntity.getId());
		} catch (InterruptedException ex) {
			log.error("Can't put pageEntity to outcomeQueue");
		}
	}

	private PageEntity tryPollPage() {
		pageEntity = incomeQueue.poll();
		if (pageEntity == null) {
			try {
				sleep(1_000);
			} catch (InterruptedException e) {
				log.error("Can't sleep after getting null pageEntity");
			}
		} else return pageEntity;
		return null;
	}

	private boolean pressedStop() {
		return IndexServiceImpl.pressedStop;
	}
}