package searchengine.services.Impl;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.*;
import searchengine.services.IndexingService;
import searchengine.tools.indexing.IndexingActions;
import searchengine.tools.indexing.SchemaActions;

import java.util.*;

@Slf4j
@Setter
@Getter
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

	private final IndexingResponse indexingResponse;
	private final SiteRepository siteRepository;
	private final IndexingActions indexingActions;
	private final SchemaActions schemaActions;
	public static volatile boolean pressedStop = false;
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<>();

	@Override
	public synchronized ResponseEntity<IndexingResponse> indexingStart() {
		log.warn("Mapping /startIndexing executed");

		if (indexingActions.getIndexingActionsStarted())
			return indexingResponse.startFailed();

		Set<SiteEntity> siteEntities = schemaActions.fullInit();
		if (siteEntities.size() == 0)
			return indexingResponse.startFailedEmptyQuery();

		singleTask.set(new Thread(() -> {
			indexingActions.startFullIndexing(siteEntities);
		}, "0day-thread"));

		singleTask.get().start();
		return indexingResponse.successfully();
	}

	@Override
	public ResponseEntity<IndexingResponse> indexingPageStart(String url) {

		log.warn("Mapping /indexPage executed");

		if (indexingActions.getIndexingActionsStarted())
			return indexingResponse.startFailed();

		if (url == null || url.equals(""))
			return indexingResponse.startFailedEmptyQuery();

		SiteEntity siteEntity = schemaActions.partialInit(url);
		if (siteEntity == null) return indexingResponse.indexPageFailed();

		singleTask.set(new Thread(() -> {
			indexingActions.startPartialIndexing(siteEntity);
		}, "0day-thread"));

		singleTask.get().start();

		return indexingResponse.successfully();
	}

	@Override
	public ResponseEntity<IndexingResponse> indexingStop() {
		log.warn("Mapping /stopIndexing executed");

		if (!indexingActions.getIndexingActionsStarted())
			return indexingResponse.stopFailed();

		setPressedStop(true);
		indexingActions.setIndexingActionsStarted(false);

		return indexingResponse.successfully();
	}

	public void setPressedStop(boolean value) {
		pressedStop = value;
	}
}
