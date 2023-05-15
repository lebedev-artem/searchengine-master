package searchengine.tools.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import searchengine.model.IndexingStatus;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.LemmasAndIndexCollectingService;
import searchengine.services.PagesSavingService;
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
    public Boolean enabled = true;
    public static String siteUrl;
    public static String homeSiteUrl;
    public static Boolean isPartialIndexing;
    private SiteEntity siteEntity;
    private final PagesSavingService pagesSavingService;
    private boolean indexingActionsStarted = false;
    private final LemmasAndIndexCollectingService lemmasAndIndexCollectingService;
    private BlockingQueue<PageEntity> queueOfPagesForSaving = new LinkedBlockingQueue<>(100);
    private BlockingQueue<Integer> queueOfPagesForLemmasCollecting = new LinkedBlockingQueue<>(100_000);
    private ScrapingAction action;
    private final Environment environment;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;

    @Override
    public void startFullIndexing(@NotNull Set<SiteEntity> siteEntities) {
        log.warn("Full indexing will be started now");
        long start = System.currentTimeMillis();
        ForkJoinPool pool = new ForkJoinPool();
        setIndexingActionsStarted(true);

        for (SiteEntity siteEntity : siteEntities) {

            if (!enabled) {
                stopPressedActions(pool);
                break;
            }

            CountDownLatch latch = new CountDownLatch(3);
            writeLogBeforeIndexing(siteEntity);

            Thread scrapingThread = new Thread(() -> crawlThreadBody(pool, siteEntity, latch), "crawl-thread");
            Thread pagesSaverThread = new Thread(() -> pagesThreadBody(siteEntity, latch), "pages-thread");
            Thread lemmasCollectorThread = new Thread(() -> lemmasThreadBody(siteEntity, latch), "lemmas-thread");

            scrapingThread.start();
            pagesSaverThread.start();
            lemmasCollectorThread.start();

            awaitLatch(latch);
            doActionsAfterIndexing(siteEntity);

        }
        shutDownAction(pool);
        writeLogAfterIndexing(start);
        setIndexingActionsStarted(false);
    }

    private void writeLogBeforeIndexing(@NotNull SiteEntity siteEntity) {
        log.info(siteEntity.getName() + " with URL " + siteEntity.getUrl() + " started indexing");
        log.info(pageRepository.count() + " pages, "
                + lemmaRepository.count() + " lemmas, "
                + indexRepository.count() + " indexes in table");
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
        action = new ScrapingAction(siteEntity.getUrl(), siteEntity, queueOfPagesForSaving, environment);
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
            setEnabled(true);
            setIndexingActionsStarted(false);

        }
        lemmaRepository.flush();
        pageRepository.flush();
        indexRepository.flush();
        siteRepository.flush();
    }

    @Override
    public boolean isIndexingActionsStarted() {
        return indexingActionsStarted;
    }

    @Override
    public void setEnabled(boolean value) {
        enabled = value;
        lemmasAndIndexCollectingService.setEnabled(value);
        pagesSavingService.setEnabled(value);
        ScrapingAction.enabled = false;
    }

    private void shutDownAction(@NotNull ForkJoinPool pool) {
        pool.shutdownNow();
    }

    private void doActionsAfterIndexing(@NotNull SiteEntity siteEntity) {
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
            log.warn("Status of site " + siteEntity.getName() + " set to " + siteEntity.getStatus().toString() + ", error set to " + siteEntity.getLastError());
        } else {
            siteEntity.setLastError("Индексация остановлена пользователем");
            siteEntity.setStatus(IndexingStatus.FAILED);
            log.warn("Status of site " + siteEntity.getName() + " set to " + siteEntity.getStatus().toString() + ", error set to " + siteEntity.getLastError());
        }
        if (isPartialIndexing) {
            siteEntity.setUrl(homeSiteUrl);
        }
        siteRepository.save(siteEntity);
        StringPool.clearAll();
    }

    private void writeLogAfterIndexing(long start) {
        log.info(siteRepository.count() + " site(s)");
        log.info(pageRepository.count() + " pages");
        log.info(lemmaRepository.count() + " lemmas");
        log.info(indexRepository.count() + " index entries");
        log.info("Just in " + (System.currentTimeMillis() - start) + " ms");
        log.info("FINISHED. I'm ready to start again and again");
    }

    private static void awaitLatch(@NotNull CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("Can't await latch");
        }
    }
}
