package searchengine.tools.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.IndexingStatus;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.tools.LemmaFinder;
import searchengine.tools.StringPool;
import searchengine.tools.UrlFormatter;

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

    private Boolean enabled = true;
    private SiteEntity siteEntity;
    private BlockingQueue<PageEntity> queueOfPagesForLemmasCollecting = new LinkedBlockingQueue<>(1_000);
    private ScrapingAction action;
    private LemmasIndexCollector lemmasIndexCollector;
    private boolean indexingActionsStarted = false;
    private final SitesList sitesList;
    private final Environment environment;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final LemmaFinder lemmaFinder;

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

            CountDownLatch latch = new CountDownLatch(2);
            writeLogBeforeIndexing(siteEntity);

            Thread scrapingThread = new Thread(() -> crawlThreadBody(pool, siteEntity, latch), "crawl-thread");
            Thread lemmasCollectorThread = new Thread(() -> lemmasThreadBody(siteEntity, latch), "lemmas-thread");

            scrapingThread.start();
            lemmasCollectorThread.start();

            awaitLatch(latch);
            doActionsAfterIndexing(siteEntity);

        }
        pool.shutdownNow();
        writeLogAfterIndexing(start);
        setIndexingActionsStarted(false);
    }

    private void lemmasThreadBody(SiteEntity siteEntity, @NotNull CountDownLatch latch) {
       lemmasIndexCollector = new LemmasIndexCollector(lemmaFinder, pageRepository, indexRepository, lemmaRepository);
        lemmasCollectingActions(siteEntity);
        latch.countDown();
        log.warn("lemmas-finding-thread finished, latch =  " + latch.getCount());
    }

    private void crawlThreadBody(ForkJoinPool pool, SiteEntity siteEntity, @NotNull CountDownLatch latch) {
        scrapActions(pool, siteEntity);
        latch.countDown();
        lemmasIndexCollector.setScrapingIsDone(true);
//        pagesSavingService.setScrapingIsDone(true);
        log.info(pageRepository.countBySiteEntity(siteEntity) + " pages saved in DB");
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
        lemmasIndexCollector.setIncomeQueue(queueOfPagesForLemmasCollecting);
        lemmasIndexCollector.setScrapingIsDone(false);
        lemmasIndexCollector.setSiteEntity(siteEntity);
        lemmasIndexCollector.startCollecting();
    }

    private void scrapActions(@NotNull ForkJoinPool pool, @NotNull SiteEntity siteEntity) {
        action = new ScrapingAction(
                siteEntity.getUrl(),
                siteEntity,
                queueOfPagesForLemmasCollecting,
                environment, pageRepository,
                getHomeSiteUrl(siteEntity.getUrl()),
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
            setIndexingActionsStarted(false);

        }
    }

    @Override
    public boolean isIndexingActionsStarted() {
        return indexingActionsStarted;
    }

    @Override
    public void setEnabled(boolean value) {
        enabled = value;
        lemmasIndexCollector.setEnabled(value);
        ScrapingAction.enabled = value;
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

        siteEntity.setUrl(getHomeSiteUrl(siteEntity.getUrl()));
        siteRepository.save(siteEntity);
        StringPool.clearAll();
    }

    private String getHomeSiteUrl(String url){
        String result = null;
        for (Site s: sitesList.getSites()) {
            if (s.getUrl().startsWith(UrlFormatter.getShortUrl(url))){
                result = s.getUrl();
                break;
            }
        }
        return result;
    }

    private void writeLogAfterIndexing(long start) {
        log.info(siteRepository.count() + " site(s)");
        log.info(pageRepository.count() + " pages");
        log.info(lemmaRepository.count() + " lemmas");
        log.info(indexRepository.count() + " index entries");
        log.info("Just in " + (System.currentTimeMillis() - start) + " ms");
        log.info("FINISHED. I'm ready to start again and again");
    }

    private void awaitLatch(@NotNull CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("Can't await latch");
        }
    }

    private void writeLogBeforeIndexing(@NotNull SiteEntity siteEntity) {
        log.info(siteEntity.getName() + " with URL " + siteEntity.getUrl() + " started indexing");
        log.info(pageRepository.count() + " pages, "
                + lemmaRepository.count() + " lemmas, "
                + indexRepository.count() + " indexes in table");
    }
}
