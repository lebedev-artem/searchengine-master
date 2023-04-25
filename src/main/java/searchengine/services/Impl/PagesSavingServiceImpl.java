package searchengine.services.Impl;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.services.PagesSavingService;
import searchengine.tools.CheckHeapSize;
import searchengine.tools.StringPool;

import java.util.Random;
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
	private volatile boolean scrapingIsDone = false;
	private BlockingQueue<PageEntity> incomeQueue;
	private BlockingQueue<Integer> outcomeQueue;
	private SiteEntity siteEntity;
	private PageEntity pageEntity;
	private final PageRepository pageRepository;
	private StringPool stringPool;
	private Integer counter = 0;
	private final CheckHeapSize checkHeapSize;

	public void startSavingPages() {
		final long startTime = System.currentTimeMillis();

		while (allowed()) {
			if (pressedStop()) {
				incomeQueue.clear();
				outcomeQueue.clear();
				return;
			}

			pageEntity = incomeQueue.poll();
			if (pageEntity != null) {
//				String fullLink = pageEntity.getSiteEntity().getUrl().concat(pageEntity.getPath().substring(1));

				if (!StringPool.savedPaths.containsKey(pageEntity.getPath())) {

					pageRepository.save(pageEntity);

					lock.readLock().lock();
					StringPool.internSavedPath(pageEntity.getPath());
					lock.readLock().unlock();

					tryPutPageIdToOutcomeQueue();
					log.warn(pageEntity.getPath() + " saved. queue has " + incomeQueue.size());
					counter++;
					if (counter > getRandom()){
						log.warn("Another " + counter + " pages saved to the database. " + pageRepository.countBySiteEntity(siteEntity) + " total saved. IncomeQueue has " + incomeQueue.size() + " objects");
						log.info("Used heap size - " + checkHeapSize.getHeap() + ". Free - " + checkHeapSize.getFreeHeap());
						counter = 0;
					}
				}
			} else {
				try {
					sleep(1_000);
				} catch (InterruptedException e) {
					log.error("Can't sleep after getting null pageEntity");
				}
			}
		}
		log.warn(logAboutEachSite(startTime));
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

	private boolean pressedStop() {
		return IndexingServiceImpl.pressedStop;
	}

	private @NotNull Integer getRandom(){
		Random r = new Random();
		int low = 240;
		int high = 260;
		return r.nextInt(high-low) + low;
		}
}