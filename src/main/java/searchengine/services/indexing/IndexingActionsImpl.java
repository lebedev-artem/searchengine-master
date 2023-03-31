package searchengine.services.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.lemmatization.LemmasAndIndexCollectingService;
import searchengine.services.savingpages.PagesSavingService;
import searchengine.services.scraping.ScrapingAction;
import searchengine.services.stuff.StaticVault;
import searchengine.services.stuff.StringPool;

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
			""
	};
	private SiteEntity siteEntity;
	private volatile boolean indexingActionsStarted = false;
	private BlockingQueue<PageEntity> queueOfPagesForSaving = new LinkedBlockingQueue<>(500);
	private BlockingQueue<Integer> queueOfPagesForLemmasCollecting = new LinkedBlockingQueue<>(100_000);
	private final PagesSavingService pagesSavingService;
	private final LemmasAndIndexCollectingService lemmasAndIndexCollectingService;
	private final PageRepository pageRepository;
	private final SiteRepository siteRepository;
	private final IndexRepository indexRepository;
	private final LemmaRepository lemmaRepository;
	public static String siteUrl;
	private final StringPool stringPool = new StringPool();


	@Override
	public void startFullIndexing(@NotNull Set<SiteEntity> siteEntities) {
		log.warn("Full indexing will be started now");
		long start = System.currentTimeMillis();
		ForkJoinPool pool = new ForkJoinPool();
		setIndexingActionsStarted(true);

		for (SiteEntity siteEntity : siteEntities) {
			CountDownLatch latch = new CountDownLatch(3);
			if (!pressedStop()) {
				log.info(siteEntity.getName() + " with URL " + siteEntity.getUrl() + " started indexing");
				log.info(pageRepository.count() + " sites, " + lemmaRepository.count() + " lemmas, " + indexRepository.count() + " indexes in table");
				Thread scrapingThread = new Thread(() -> {
					scrapActions(pool, siteEntity);
					latch.countDown();
					pagesSavingService.setScrapingIsDone(true);
					log.warn("crawl-thread finished, latch =  " + latch.getCount());
				}, "crawl-thread");

				Thread pagesSaverThread = new Thread(() -> {
					pageSavingActions(siteEntity);
					latch.countDown();
					lemmasAndIndexCollectingService.setSavingPagesIsDone(true);
					log.warn("saving-pages-thread finished, latch =  " + latch.getCount());
				}, "pages-thread");

				Thread lemmasCollectorThread = new Thread(() -> {
					lemmasCollectingActions(siteEntity);
					latch.countDown();
					log.warn("lemmas-finding-thread finished, latch =  " + latch.getCount());
				}, "lemmas-thread");

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

	@Override
	public void startPartialIndexing(SiteEntity siteEntity) {
		log.warn("Partial indexing will be started now");
		Set<SiteEntity> oneEntitySet = new HashSet<>();
		oneEntitySet.add(siteEntity);
		startFullIndexing(oneEntitySet);
	}

	private void lemmasCollectingActions(SiteEntity siteEntity) {
		StaticVault.indexEntitiesMap.clear();
		StaticVault.lemmaEntitiesMap.clear();
		lemmasAndIndexCollectingService.setIncomeQueue(queueOfPagesForLemmasCollecting);
		lemmasAndIndexCollectingService.setSavingPagesIsDone(false);
		lemmasAndIndexCollectingService.setSiteEntity(siteEntity);
		lemmasAndIndexCollectingService.startCollecting();
	}

	private void pageSavingActions(SiteEntity siteEntity) {
		pagesSavingService.setScrapingIsDone(false);
		pagesSavingService.setIncomeQueue(queueOfPagesForSaving);
		pagesSavingService.setOutcomeQueue(queueOfPagesForLemmasCollecting);
		pagesSavingService.setSiteEntity(siteEntity);
		pagesSavingService.startSavingPages();
	}

	private void scrapActions(ForkJoinPool pool, SiteEntity siteEntity) {
		siteUrl = siteEntity.getUrl();
		pool.invoke(new ScrapingAction(siteEntity.getUrl(), siteEntity, queueOfPagesForSaving, pageRepository, siteRepository, stringPool));
	}

	private void stopPressedActions(ForkJoinPool pool) {

		try {
			log.warn("STOP pressed by user");
			Thread.sleep(5_000);
		} catch (InterruptedException e) {
			log.error("I don't want to sleep");
		} finally {
			shutDownAction(pool);
			IndexServiceImpl.pressedStop = false;
			setIndexingActionsStarted(false);
			pageRepository.flush();
			lemmaRepository.flush();
			indexRepository.flush();
		}
	}

	@Override
	public boolean getIndexingActionsStarted() {
		return indexingActionsStarted;
	}

	private void shutDownAction(@NotNull ForkJoinPool pool) {
		pool.shutdownNow();
		System.gc();
	}

	private void startActionsAfterIndexing(@NotNull SiteEntity siteEntity) {
		String status = "INDEXED";
		String lastError = siteEntity.getLastError();
		int countPages = pageRepository.countBySiteEntity(siteEntity);
		switch (countPages) {
			case 0 -> {
				status = "FAILED";
				lastError = errors[1];
			}
			case 1 -> {
				status = "FAILED";
				lastError = errors[0];
			}
		}
		if (!pressedStop()) {
			siteRepository.updateStatusStatusTimeErrorByUrl(status, LocalDateTime.now(), lastError, siteEntity.getUrl());
			log.warn("Status of site " + siteEntity.getName() + " set to " + status + ", error set to " + lastError);
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

	private boolean pressedStop() {
		return IndexServiceImpl.pressedStop;
	}
}
