package searchengine.services.searchIndexGeneration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SearchIndexEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.sleep;

@Getter
@Setter
@Component
@NoArgsConstructor
public class IndexGenerationServiceImpl {
	private static final Logger rootLogger = LogManager.getRootLogger();
	private boolean lemmasCollectingIsDone = false;
	private volatile boolean indexingStopped = false;
	private BlockingQueue<SearchIndexEntity> queue;
	private SiteEntity siteEntity;

	@Autowired
	SearchIndexRepository searchIndexRepository;
	@Autowired
	SiteRepository siteRepository;
	@Autowired
	PageRepository pageRepository;

//	@CacheEvict(value = "siteEntityCache", allEntries = true)
//	@Transactional
//	@Modifying
	public void indexGenerate() {
		long timeIndexCreatingAndSaving = System.currentTimeMillis();
		Set<SearchIndexEntity> indexEntitiesSet = new HashSet<>();
		while (true) {
			SearchIndexEntity searchIndexEntity = queue.poll();
			if (searchIndexEntity != null) {
				indexEntitiesSet.add(searchIndexEntity);
			} else {
				try {
					sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (notAllowed() || indexingStopped) {
				rootLogger.warn("index created");
//				for (SearchIndexEntity sIE: indexEntitiesSet) {
//					searchIndexRepository.save(sIE);
//				}
				searchIndexRepository.saveAll(indexEntitiesSet);
				rootLogger.warn(":: " + indexEntitiesSet.size() + " index entries generated and saved in DB, site -> " + siteEntity.getName() + " in " + (System.currentTimeMillis() - timeIndexCreatingAndSaving) + " ms");
				return;
			}
		}
	}

		private boolean notAllowed () {
			return !queue.iterator().hasNext() && lemmasCollectingIsDone;
		}
	}
