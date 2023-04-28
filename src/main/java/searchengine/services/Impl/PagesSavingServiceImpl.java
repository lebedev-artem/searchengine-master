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
	private boolean scrapingIsDone = false;
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
				actionsAfterStop();
				return;
			}

			pageEntity = incomeQueue.poll();
			if (pageEntity != null) {
				if (!StringPool.savedPaths.containsKey(pageEntity.getPath())) {

					pageRepository.save(pageEntity);
					addPathToStaticVaultAsSaved();
					putPageIdToOutcomeQueue();
					writeLogAboutEachPage();

				}
			} else {
				sleeping(1_000, "Can't sleep after getting null pageEntity");
			}
		}
		log.warn(logAboutEachSite(startTime));
	}

	private void addPathToStaticVaultAsSaved() {
		lock.readLock().lock();
		StringPool.internSavedPath(pageEntity.getPath());
		lock.readLock().unlock();
	}

	private void writeLogAboutEachPage() {
		log.warn(pageEntity.getPath() + " saved. queue has " + incomeQueue.size());
		counter++;

		if (counter > getRandom()){
			log.warn("Another "
					+ counter + " pages saved to the database. "
					+ pageRepository.countBySiteEntity(siteEntity) + " total saved. IncomeQueue has "
					+ incomeQueue.size() + " objects");
			log.info("Used heap size - "
					+ checkHeapSize.getHeap() + ". Free - "
					+ checkHeapSize.getFreeHeap());
			counter = 0;
		}
	}

	private void actionsAfterStop() {
		incomeQueue.clear();
		outcomeQueue.clear();
	}


	private @NotNull Boolean allowed() {
		return !scrapingIsDone | incomeQueue.iterator().hasNext();
	}

	private @NotNull String logAboutEachSite(long startTime) {
		return pageRepository.countBySiteEntity(siteEntity)
				+ " pages saved in DB from site url " + siteEntity.getUrl()
				+ " in " + (System.currentTimeMillis() - startTime) + " ms";
	}

	private void putPageIdToOutcomeQueue() {
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

	private static void sleeping(int millis, String s) {
		try {
			sleep(millis);
		} catch (InterruptedException e) {
			log.error(s);
		}
	}
}