package searchengine.tools.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import searchengine.model.IndexingStatus;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.Impl.IndexingServiceImpl;
import searchengine.services.LemmasAndIndexCollectingService;
import searchengine.services.PagesSavingService;
import searchengine.services.RepositoryService;
import searchengine.tools.StaticVault;
import searchengine.tools.StringPool;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Setter
@Getter
@Component
@RequiredArgsConstructor
public class IndexingActionsImpl implements IndexingActions {

	private final String[] errors = {
			"Ошибка индексации: главная страница сайта не доступна",
			"Ошибка индексации: сайт не доступен",
			""};
	public static String siteUrl;
	private SiteEntity siteEntity;
	private final RepositoryService repositoryService;
	private final PagesSavingService pagesSavingService;
	private volatile boolean indexingActionsStarted = false;
	private final LemmasAndIndexCollectingService lemmasAndIndexCollectingService;
	private BlockingQueue<PageEntity> queueOfPagesForSaving = new LinkedBlockingQueue<>(100);
	private BlockingQueue<Integer> queueOfPagesForLemmasCollecting = new LinkedBlockingQueue<>(100_000);

	@Override
	public void startFullIndexing(@NotNull Set<SiteEntity> siteEntities) {
		log.warn("Full indexing will be started now");
		long start = System.currentTimeMillis();
		ForkJoinPool pool = new ForkJoinPool();
		setIndexingActionsStarted(true);

		for (SiteEntity siteEntity : siteEntities) {
			CountDownLatch latch = new CountDownLatch(3);
			if (!pressedStop()) {

				writeLogBeforeIndexing(siteEntity);

				Thread scrapingThread = new Thread(() -> crawlThreadBody(pool, siteEntity, latch), "crawl-thread");
				Thread pagesSaverThread = new Thread(() -> pagesThreadBody(siteEntity, latch), "pages-thread");
				Thread lemmasCollectorThread = new Thread(() -> lemmasThreadBody(siteEntity, latch), "lemmas-thread");

				scrapingThread.start();
				pagesSaverThread.start();
				lemmasCollectorThread.start();

				try {
					latch.await();
				} catch (InterruptedException e) {
					log.error("Can't await latch");
				}
				startActionsAfterIndexing(siteEntity);
			} else {
				stopPressedActions(pool);
				break;
			}

		}
		shutDownAction(pool);
		writeLogAfterIndexing(start);
		setIndexingActionsStarted(false);
		System.gc();
	}

	private void writeLogBeforeIndexing(@NotNull SiteEntity siteEntity) {
		log.info(siteEntity.getName() + " with URL " + siteEntity.getUrl() + " started indexing");
		log.info(repositoryService.countPages() + " pages, "
				+ repositoryService.countLemmas() + " lemmas, "
				+ repositoryService.countIndexes() + " indexes in table");
	}

	private void lemmasThreadBody(SiteEntity siteEntity, @NotNull CountDownLatch latch) {
		lemmasCollectingActions(siteEntity);
		latch.countDown();
		log.warn("lemmas-finding-thread finished, latch =  " + latch.getCount());
	}

	private void pagesThreadBody(SiteEntity siteEntity, @NotNull CountDownLatch latch) {
		pageSavingActions(siteEntity);
		latch.countDown();
		lemmasAndIndexCollectingService.setSavingPagesIsDone(true);
		log.warn("saving-pages-thread finished, latch =  " + latch.getCount());
	}

	private void crawlThreadBody(ForkJoinPool pool, SiteEntity siteEntity, @NotNull CountDownLatch latch) {
		scrapActions(pool, siteEntity);
		latch.countDown();
		pagesSavingService.setScrapingIsDone(true);
		log.warn("crawl-thread finished, latch =  " + latch.getCount());
	}

	@Override
	public void startPartialIndexing(SiteEntity siteEntity) {
		log.warn("Partial indexing will be started now");
		Set<SiteEntity> oneEntitySet = new HashSet<>();
		oneEntitySet.add(siteEntity);
		startFullIndexing(oneEntitySet);
	}

	private void lemmasCollectingActions(SiteEntity siteEntity) {
		lemmasAndIndexCollectingService.setIncomeQueue(queueOfPagesForLemmasCollecting);
		lemmasAndIndexCollectingService.setSavingPagesIsDone(false);
		lemmasAndIndexCollectingService.setSiteEntity(siteEntity);
		lemmasAndIndexCollectingService.startCollecting();
		StaticVault.indexEntitiesMap.clear();
		StaticVault.lemmaEntitiesMap.clear();
	}

	private void pageSavingActions(SiteEntity siteEntity) {
		pagesSavingService.setOutcomeQueue(queueOfPagesForLemmasCollecting);
		pagesSavingService.setIncomeQueue(queueOfPagesForSaving);
		pagesSavingService.setScrapingIsDone(false);
		pagesSavingService.setSiteEntity(siteEntity);
		pagesSavingService.startSavingPages();

	}

	private void scrapActions(@NotNull ForkJoinPool pool, @NotNull SiteEntity siteEntity) {
		siteUrl = siteEntity.getUrl();
		ScrapingAction action = new ScrapingAction(siteEntity.getUrl(), siteEntity, queueOfPagesForSaving);
		pool.invoke(action);
	}

	private void stopPressedActions(ForkJoinPool pool) {

		try {
			log.warn("STOP pressed by user");
			Thread.sleep(5_000);
		} catch (InterruptedException e) {
			log.error("I don't want to sleep");
		} finally {
			shutDownAction(pool);
			IndexingServiceImpl.pressedStop = false;
			setIndexingActionsStarted(false);
			repositoryService.flushRepositories();
		}
	}

	@Override
	public boolean isIndexingActionsStarted() {
		return indexingActionsStarted;
	}

	private void shutDownAction(@NotNull ForkJoinPool pool) {
		pool.shutdownNow();
		System.gc();
	}

	private void startActionsAfterIndexing(@NotNull SiteEntity siteEntity) {
		siteEntity.setStatus(IndexingStatus.INDEXED);
		siteEntity.setLastError("");
		siteEntity.setStatusTime(LocalDateTime.now());
		int countPages = repositoryService.countPagesFromSite(siteEntity);
		switch (countPages) {
			case 0 -> {
				siteEntity.setStatus(IndexingStatus.FAILED);
				siteEntity.setLastError(errors[1]);
			}
			case 1 -> {
				siteEntity.setStatus(IndexingStatus.FAILED);
				siteEntity.setLastError(errors[0]);
			}
		}
		if (!pressedStop()) {
			log.warn("Status of site " + siteEntity.getName() + " set to " + siteEntity.getStatus().toString() + ", error set to " + siteEntity.getLastError());
		} else {
			siteEntity.setLastError("Индексация остановлена пользователем");
			siteEntity.setStatus(IndexingStatus.FAILED);
			log.warn("Status of site " + siteEntity.getName() + " set to " + siteEntity.getStatus().toString() + ", error set to " + siteEntity.getLastError());
		}
		repositoryService.saveSite(siteEntity);
		StringPool.clearAll();
	}

	private void writeLogAfterIndexing(long start) {
		log.info(repositoryService.countSites() + " site(s)");
		log.info(repositoryService.countPages() + " pages");
		log.info(repositoryService.countLemmas() + " lemmas");
		log.info(repositoryService.countIndexes() + " index entries");
		log.info("Just in " + (System.currentTimeMillis() - start) + " ms");
		log.info("FINISHED. I'm ready to start again and again");
	}

	private boolean pressedStop() {
		return IndexingServiceImpl.pressedStop;
	}
}
