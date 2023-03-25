package searchengine.services.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import searchengine.model.IndexingStatus;
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

	private SiteEntity siteEntity;
	private volatile boolean indexingActionsStarted = false;
	private volatile boolean pressedStop = false;
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
		setIndexingActionsStarted(true);
		long start = System.currentTimeMillis();
		ForkJoinPool pool = new ForkJoinPool();

		for (SiteEntity siteEntity : siteEntities) {
			CountDownLatch latch = new CountDownLatch(3);
			if (pressedStop()) {
				try {
					Thread.sleep(5_000);
				} catch (InterruptedException e) {
					log.error("I don't want to sleep");
				} finally {
					shutDownAction(pool);
				}
				break;
			}

			Thread scrapingThread = new Thread(() -> {
//				ScrapTask rootScrapTask = new ScrapTask(siteEntity.getUrl());
				siteUrl = siteEntity.getUrl();
				pool.invoke(new ScrapingAction(siteEntity.getUrl(), siteEntity, queueOfPagesForSaving, pageRepository, siteRepository, stringPool));
				latch.countDown();
				log.warn("crawl-thread finished, latch =  " + latch.getCount());
				pagesSavingService.setScrapingIsDone(true);
			}, "crawl-thread");

			Thread pagesSaverThread = new Thread(() -> {
				pagesSavingService.setScrapingIsDone(false);
				pagesSavingService.setIncomeQueue(queueOfPagesForSaving);
				pagesSavingService.setOutcomeQueue(queueOfPagesForLemmasCollecting);
				pagesSavingService.setSiteEntity(siteEntity);
				pagesSavingService.startSavingPages();
				latch.countDown();
				lemmasAndIndexCollectingService.setSavingPagesIsDone(true);
				log.warn("saving-pages-thread finished, latch =  " + latch.getCount());
			}, "pages-thread");

			Thread lemmasCollectorThread = new Thread(() -> {
				lemmasAndIndexCollectingService.setIncomeQueue(queueOfPagesForLemmasCollecting);
				lemmasAndIndexCollectingService.setSavingPagesIsDone(false);
				lemmasAndIndexCollectingService.setSiteEntity(siteEntity);
				lemmasAndIndexCollectingService.startCollecting();
				latch.countDown();
				log.warn("lemmas-finding-thread finished, latch =  " + latch.getCount());
			}, "lemmas-thread");

			scrapingThread.start();
			pagesSaverThread.start();
			lemmasCollectorThread.start();

			try {
				latch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			startActionsAfterScraping(siteEntity);
		}
		shutDownAction(pool);
		log.info(siteRepository.count() + " site(s)");
		log.info(pageRepository.count() + " pages");
		log.info(lemmaRepository.count() + " lemmas");
		log.info(indexRepository.count() + " index entries");
		log.info("Just in " + (System.currentTimeMillis() - start) + " ms");
		log.error("FINISHED. I'm ready to start again and again");
		IndexServiceImpl.pressedStop = false;
		setIndexingActionsStarted(false);
		System.gc();
	}

	@Override
	public void startPartialIndexing() {
	}

	@Override
	public void setPressedStop(boolean value) {
		pressedStop = value;
	}

	@Override
	public boolean getIndexingActionsStarted() {
		return indexingActionsStarted;
	}

	private void shutDownAction(@NotNull ForkJoinPool pool) {
		indexingActionsStarted = false;
		pool.shutdownNow();
		System.gc();
	}

	private void startActionsAfterScraping(@NotNull SiteEntity siteEntity) {
		String status = pageRepository.existsBySiteEntity(siteEntity) ? IndexingStatus.INDEXED.status : IndexingStatus.FAILED.status;
		if (!pressedStop()) {
			siteRepository.updateStatusStatusTimeErrorByUrl(status, LocalDateTime.now(), "", siteEntity.getUrl());
			log.warn("Status of site " + siteEntity.getName() + " set to " + status);
		}
	}

	private boolean pressedStop() {
		return IndexServiceImpl.pressedStop;
	}
}
