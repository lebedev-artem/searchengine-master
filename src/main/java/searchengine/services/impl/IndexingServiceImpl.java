package searchengine.services.impl;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.tools.LemmaFinder;
import searchengine.tools.StringPool;
import searchengine.tools.UrlFormatter;
import searchengine.tools.LemmasIndexActions;
import searchengine.tools.SchemaActions;
import searchengine.tools.CrawlAction;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Setter
@Getter
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

	private final String[] errors = {
			"Ошибка индексации: главная страница сайта не доступна",
			"Ошибка индексации: сайт не доступен",
			""};

	private SchemaActions schemaActions;
	private final IndexingResponse indexingResponse;
	private Thread SINGLE_TASK;

	private BlockingQueue<PageEntity> queueOfPagesForLemmasCollecting = new LinkedBlockingQueue<>(1_000);
	private CrawlAction action;
	private LemmasIndexActions lemmasIndexActions;
	private Boolean enabled = true;
	private boolean isIndexingStarted = false;
	private final SitesList sitesList;
	private final Environment environment;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final IndexRepository indexRepository;
	private final SiteRepository siteRepository;
	private final LemmaFinder lemmaFinder;
	private UrlFormatter urlFormatter = new UrlFormatter();

	@Override
	public ResponseEntity<IndexingResponse> indexingStart() {
		log.warn("Mapping /startIndexing executed");
		schemaActions = new SchemaActions(sitesList, environment, siteRepository, pageRepository, lemmaRepository, indexRepository);
		if (isIndexingStarted())
			return indexingResponse.startFailed();

		Set<SiteEntity> siteEntities = schemaActions.fullInit();
		if (siteEntities.size() == 0)
			return indexingResponse.startFailedEmptyQuery();

		SINGLE_TASK = new Thread(() -> startFullIndexing(siteEntities), "0day-thread");
		SINGLE_TASK.start();

		return indexingResponse.successfully();
	}

	@Override
	public ResponseEntity<IndexingResponse> indexingPageStart(String url) {
		log.warn("Mapping /indexPage executed");
		schemaActions = new SchemaActions(sitesList, environment, siteRepository, pageRepository, lemmaRepository, indexRepository);

		if (isIndexingStarted())
			return indexingResponse.startFailed();

		if (url == null || url.equals(""))
			return indexingResponse.startFailedEmptyQuery();

		SiteEntity siteEntity = schemaActions.partialInit(url);
		if (siteEntity == null) return indexingResponse.indexPageFailed();
		SINGLE_TASK = new Thread(() -> startPartialIndexing(siteEntity), "0day-thread");
		SINGLE_TASK.start();
		return indexingResponse.successfully();
	}

	@Override
	public ResponseEntity<IndexingResponse> indexingStop() {
		log.warn("Mapping /stopIndexing executed");

		if (!isIndexingStarted())
			return indexingResponse.stopFailed();

		setEnabled(false);
		setIndexingStarted(false);

		return indexingResponse.successfully();
	}

	public void startFullIndexing(@NotNull Set<SiteEntity> siteEntities) {
		log.warn("Full indexing will be started now");
		long start = System.currentTimeMillis();
		ForkJoinPool pool = new ForkJoinPool();
		setIndexingStarted(true);

		for (SiteEntity siteEntity : siteEntities) {

			if (!enabled) {
				stopPressedActions(pool);
				break;
			}

			CountDownLatch latch = new CountDownLatch(2);
			writeLogBeforeIndexing(siteEntity);

			Thread crawlingThread = new Thread(() -> crawlThreadBody(pool, siteEntity, latch), "crawl-thread");
			Thread lemmasCollectorThread = new Thread(() -> lemmasThreadBody(siteEntity, latch), "lemmas-thread");

			crawlingThread.start();
			lemmasCollectorThread.start();

			awaitLatch(latch);
			doActionsAfterIndexing(siteEntity);

		}
		pool.shutdownNow();
		writeLogAfterIndexing(start);
		setIndexingStarted(false);
	}

	public void startPartialIndexing(SiteEntity siteEntity) {
		log.warn("Partial indexing will be started now");
		Set<SiteEntity> oneEntitySet = new HashSet<>();
		oneEntitySet.add(siteEntity);
		startFullIndexing(oneEntitySet);
	}

	private void lemmasThreadBody(SiteEntity siteEntity, @NotNull CountDownLatch latch) {
		lemmasIndexActions = new LemmasIndexActions(lemmaFinder, pageRepository, indexRepository, lemmaRepository);
		lemmasCollectingActions(siteEntity);
		latch.countDown();
		log.warn("lemmas-collecting-thread finished, latch =  " + latch.getCount());
	}

	private void crawlThreadBody(ForkJoinPool pool, SiteEntity siteEntity, @NotNull CountDownLatch latch) {
		crawlActions(pool, siteEntity);
		latch.countDown();
		lemmasIndexActions.setCrawlingIsDone(true);
		log.info(pageRepository.countBySiteEntity(siteEntity) + " pages saved in DB");
		log.warn("crawl-thread finished, latch =  " + latch.getCount());
	}

	private void lemmasCollectingActions(SiteEntity siteEntity) {
		lemmasIndexActions.setIncomeQueue(queueOfPagesForLemmasCollecting);
		lemmasIndexActions.setCrawlingIsDone(false);
		lemmasIndexActions.setSiteEntity(siteEntity);
		lemmasIndexActions.startCollecting();
	}

	private void crawlActions(@NotNull ForkJoinPool pool, @NotNull SiteEntity siteEntity) {
		urlFormatter.setSitesList(sitesList);
		action = new CrawlAction(
				siteEntity.getUrl(),
				siteEntity,
				queueOfPagesForLemmasCollecting,
				environment, pageRepository,
				urlFormatter.getHomeSiteUrl(siteEntity.getUrl()),
				siteEntity.getUrl());
		pool.invoke(action);
	}

	private void stopPressedActions(ForkJoinPool pool) {

		try {
			log.warn("STOP pressed by user");
			Thread.sleep(5_000);
		} catch (InterruptedException e) {
			log.error("I don't want to sleep");
		} finally {
			pool.shutdownNow();
			setEnabled(true);
			setIndexingStarted(false);
		}
	}

	private void doActionsAfterIndexing(@NotNull SiteEntity siteEntity) {
		urlFormatter.setSitesList(sitesList);
		siteEntity.setStatus(IndexingStatus.INDEXED);
		siteEntity.setLastError("");
		siteEntity.setStatusTime(LocalDateTime.now());
		int countPages = pageRepository.countBySiteEntity(siteEntity);
		switch (countPages) {
			case 0 -> {
				siteEntity.setStatus(IndexingStatus.FAILED);
				siteEntity.setLastError(errors[0]);
			}
			case 1 -> {
				siteEntity.setStatus(IndexingStatus.FAILED);
				siteEntity.setLastError(errors[1]);
			}
		}
		if (enabled) {
			log.warn("Status of site " + siteEntity.getName()
					+ " set to " + siteEntity.getStatus().toString()
					+ ", error set to " + siteEntity.getLastError());
		} else {
			siteEntity.setLastError("Индексация остановлена пользователем");
			siteEntity.setStatus(IndexingStatus.FAILED);
			log.warn("Status of site " + siteEntity.getName()
					+ " set to " + siteEntity.getStatus().toString()
					+ ", error set to " + siteEntity.getLastError());
		}

		siteEntity.setUrl(urlFormatter.getHomeSiteUrl(siteEntity.getUrl()));
		siteRepository.save(siteEntity);
		StringPool.clearAll();
	}

	private void awaitLatch(@NotNull CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			log.error("Can't await latch");
		}
	}

	private void writeLogAfterIndexing(long start) {
		log.info(siteRepository.count() + " site(s)");
		log.info(pageRepository.count() + " pages");
		log.info(lemmaRepository.count() + " lemmas");
		log.info(indexRepository.count() + " index entries");
		log.info("Just in " + (System.currentTimeMillis() - start) + " ms");
		log.info("FINISHED. I'm ready to start again and again");
	}

	private void writeLogBeforeIndexing(@NotNull SiteEntity siteEntity) {
		log.info(siteEntity.getName() + " with URL " + siteEntity.getUrl() + " started indexing");
		log.info(pageRepository.count() + " pages, "
				+ lemmaRepository.count() + " lemmas, "
				+ indexRepository.count() + " indexes in table");
	}

	public boolean isIndexingStarted() {
		return isIndexingStarted;
	}

	public void setEnabled(boolean value) {
		enabled = value;
		lemmasIndexActions.setEnabled(value);
		CrawlAction.enabled = value;
	}

}
