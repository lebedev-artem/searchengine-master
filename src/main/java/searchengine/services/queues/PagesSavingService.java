package searchengine.services.queues;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Component
@NoArgsConstructor
public class PagesSavingService {
	private static final Logger rootLogger = LogManager.getRootLogger();
	private boolean scrapingIsDone = false;
	private volatile boolean indexingStopped = false;
	private final Integer COUNT_TO_SAVE = 50;
	private BlockingQueue<PageEntity> queue;
	private BlockingQueue<PageEntity> queueForIndexing;
//	private Site site;
	private SiteEntity siteEntity;

	@Autowired
	PageRepository pageRepository;
	@Autowired
	SiteRepository siteRepository;

	public void pagesSaving() {
		long time = System.currentTimeMillis();
		Set<PageEntity> entities = new HashSet<>();

		while (true) {
			PageEntity pageEntity = queue.poll();
			if (pageEntity != null) {
				if (!pageRepository.existsByPathAndSiteEntity(pageEntity.getPath(), siteEntity))
					entities.add(pageEntity);
			} else {
				try {
					sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (entities.size() == COUNT_TO_SAVE) {
				pageRepository.saveAll(entities);
				dropPageEntitiesToLemmasQueue(entities);
				entities.clear();
			}

			if (notAllowed() || indexingStopped) {
				pageRepository.saveAll(entities);
				dropPageEntitiesToLemmasQueue(entities);
				rootLogger.warn("::: " + pageRepository.countBySiteEntity(siteEntity) + " pages saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - time) + " ms");
				return;
			}
		}
	}

	private void dropPageEntitiesToLemmasQueue(@NotNull Set<PageEntity> entities) {
		for (PageEntity e : entities) {
			try {
				queueForIndexing.put(e);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private boolean notAllowed() {
		return scrapingIsDone && !queue.iterator().hasNext();
	}
}
