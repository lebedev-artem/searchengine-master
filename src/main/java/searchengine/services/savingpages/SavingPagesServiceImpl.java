package searchengine.services.savingpages;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Service
@NoArgsConstructor
public class SavingPagesServiceImpl implements SavingPagesService {
	private static final Logger rootLogger = LogManager.getRootLogger();
	private static final Logger logger = LogManager.getLogger("search_engine");
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private boolean scrapingIsDone = false;
	private volatile boolean pressedStop = false;
	private BlockingQueue<PageEntity> incomeQueue;
	private BlockingQueue<Integer> outcomeQueue;
	private SiteEntity siteEntity;

	@Autowired
	PageRepository pageRepository;
	@Autowired
	SiteRepository siteRepository;

	public void savePages() {
		scrapingIsDone = false;
		long time = System.currentTimeMillis();

		while (true) {
			PageEntity pageEntity = incomeQueue.poll();

			if (pageEntity != null) {
				long save = System.currentTimeMillis();
				if (!pageRepository.existsByPathAndSiteEntity(pageEntity.getPath(), siteEntity)) {
					lock.readLock().lock();
					pageRepository.save(pageEntity);

//					System.out.println("page queue income = " + incomeQueue.size());
					lock.readLock().unlock();
					try {
//						while (true){
//							if ((outcomeQueue.remainingCapacity() < 10) && (!pressedStop)){
//								Thread.sleep(5_000);
//							} else break;
//						}
						outcomeQueue.put(pageEntity.getId());
//						System.out.println("---page saved in " + (System.currentTimeMillis() - save) + " ms");
//						System.out.println("income queue " + incomeQueue.size());
//						System.out.println("outcome queue " + outcomeQueue.size());

//						System.out.println("lemma out from page queue = " + outcomeQueue.size());
					} catch (InterruptedException ex) {
						rootLogger.error("Can't put pageEntity to outcomeQueue");
					}
				}
			} else {
				try {
					sleep(10);
				} catch (InterruptedException e) {
					rootLogger.error("Can't sleep after getting null pageEntity");
				}
			}

			if (previousStepDoneAndQueueEmpty() || pressedStop) {
//				outcomeQueue.clear();
//				incomeQueue.clear();
				rootLogger.warn("::: "
						+ pageRepository.countBySiteEntity(siteEntity)
						+ " pages saved in DB, site -> " + siteEntity.getName()
						+ " in " + (System.currentTimeMillis() - time) + " ms");
				return;
			}
		}
	}

	private boolean previousStepDoneAndQueueEmpty() {
		return scrapingIsDone && !incomeQueue.iterator().hasNext();
	}
}