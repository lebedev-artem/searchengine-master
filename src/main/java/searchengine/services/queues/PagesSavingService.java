package searchengine.services.queues;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.model.PageEntity;
import searchengine.repositories.PageRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Component
@NoArgsConstructor
public class PagesSavingService {
	private BlockingQueue<PageEntity> queue;
	private boolean scrapingFutureIsDone = false;
//	private Future<?> expectedFuture;
	private Site site;
	private static final Logger rootLogger = LogManager.getRootLogger();
	@Autowired
	PageRepository pageRepository;

	public void pagesSaving() {

		long time = System.currentTimeMillis();
		Set<PageEntity> entities = new HashSet<>();

		while (true) {
			PageEntity pageEntity;
			pageEntity = queue.poll();
			if (pageEntity != null) {
				if (!pageRepository.existsById(pageEntity.getId()))
					entities.add(pageEntity);
//						pageRepository.save(pageEntity);
			} else {
				try {
					sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (entities.size() == 30) {
				pageRepository.saveAll(entities);
				entities.clear();
			}

			if (isNotAllowed()) {
				pageRepository.saveAll(entities);
				rootLogger.info("::: Pages saved finished in " + (System.currentTimeMillis() - time) + " ms");
				rootLogger.warn(pageRepository.countAllPages() + " pages in DB");
				return;
			}
		}
	}

	private boolean isNotAllowed() {
		return scrapingFutureIsDone && !queue.iterator().hasNext();
	}

}
