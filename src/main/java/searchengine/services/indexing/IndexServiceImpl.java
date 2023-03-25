package searchengine.services.indexing;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;
import searchengine.repositories.*;

import javax.persistence.criteria.CriteriaBuilder;
import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Setter
@Getter
@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService {

	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<>();
	public static volatile boolean pressedStop = false;
	private final IndexResponse indexResponse;
	private final IndexingActions indexingActions;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final IndexRepository indexRepository;
	private final LemmaRepository lemmaRepository;

	@Override
	@Transactional
	public synchronized ResponseEntity<?> indexingStart(@NotNull Set<SiteEntity> siteEntities) {

		if (indexingActions.getIndexingActionsStarted())
			return indexResponse.startFailed();

		singleTask.set(new Thread(() -> {
			indexingActions.startFullIndexing(siteEntities);
		}, "0day-thread"));

		singleTask.get().start();
		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingPageStart(@NotNull HttpServletRequest request) throws MalformedURLException {
//		SingleSiteListCreator singleSiteListCreator = new SingleSiteListCreator(sitesList);
		String url = request.getParameter("url");
		String hostName = new URL(url).getHost();

//		SitesList singleSiteList = singleSiteListCreator.getSiteList(url, hostName);

//		if (singleSiteList == null)
//			return indexResponse.indexPageFailed();

		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingStop() {
		if (!indexingActions.getIndexingActionsStarted())
			return indexResponse.stopFailed();

		setPressedStop(true);
		indexingActions.setIndexingActionsStarted(false);

		siteRepository.updateAllStatusStatusTimeError(IndexingStatus.FAILED.status, LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	public void test(Integer id) {
//	pageRepository.delete(pageRepository.getReferenceById(id));
//		siteRepository.deleteById(id);
//		SiteEntity siteEntity = siteRepository.findById(id);
		PageEntity pageEntity = pageRepository.findById(id);
		if (pageEntity != null){
			Set<Integer> lemmaEntities = lemmaRepository.findAllLemmaIdByPageId(id);
			pageRepository.deleteById(pageEntity.getId());
			lemmaEntities.forEach(x -> {
				LemmaEntity lemmaEntity = lemmaRepository.findById(x);
				if (lemmaEntity != null) {
					int oldFreq = lemmaEntity.getFrequency();
					if (oldFreq == 1) {
						lemmaRepository.delete(lemmaEntity);
					} else lemmaEntity.setFrequency(oldFreq - 1);
				}
			});
		}

//		pageRepository.deleteAllBySiteEntity(siteEntity);
//		pageRepository.deleteAllInBatch(pageRepository.findAllBySiteEntity(siteRepository.getReferenceById(id)));
//		pageRepository.deleteById(id);

	}

	@Override
	public void setPressedStop(boolean value) {
		pressedStop = value;
	}
}
