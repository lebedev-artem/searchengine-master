package searchengine.services.queues;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Component
@NoArgsConstructor
public class PagesSavingService {
	private static final Logger rootLogger = LogManager.getRootLogger();
	private boolean scrapingIsDone = false;
	private volatile boolean indexingStopped = false;
	private BlockingQueue<PageEntity> queue;
	private BlockingQueue<PageEntity> queueForIndexing;
	private SiteEntity siteEntity;
	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	@Autowired
	PageRepository pageRepository;
	@Autowired
	SiteRepository siteRepository;

	public void pagesSaving() {
		scrapingIsDone = false;
		long time = System.currentTimeMillis();

		while (true) {
			PageEntity pageEntity = queue.poll();

			if (pageEntity != null) {
				if (!pageRepository.existsByPathAndSiteEntity(pageEntity.getPath(), siteEntity)) {
					lock.readLock().lock();
					pageRepository.save(pageEntity);
					lock.readLock().unlock();
					try {
						queueForIndexing.put(pageEntity);
					} catch (InterruptedException ex) {
						throw new RuntimeException(ex);
					}
				}

			} else {
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (previousStepDoneAndQueueEmpty() || indexingStopped) {
				rootLogger.warn("::: "
						+ pageRepository.countBySiteEntity(siteEntity)
						+ " pages saved in DB, site -> " + siteEntity.getName()
						+ " in " + (System.currentTimeMillis() - time) + " ms");
				return;
			}
		}
	}

	private boolean previousStepDoneAndQueueEmpty() {
		return scrapingIsDone && !queue.iterator().hasNext();
	}
}

//	private void dropPageEntitiesToLemmasQueue(@NotNull Set<PageEntity> entities) {
//		for (PageEntity e : entities) {
//			try {
//				queueForIndexing.put(e);
//			} catch (InterruptedException ex) {
//				throw new RuntimeException(ex);
//			}
//		}
//	}


//			if (entities.size() == COUNT_TO_SAVE) {
//				pageRepository.saveAll(entities);
//				dropPageEntitiesToLemmasQueue(entities);
//				entities.clear();
//			}

//	Set<PageEntity> entities = new HashSet<>();