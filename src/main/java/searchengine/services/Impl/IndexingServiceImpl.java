package searchengine.services.Impl;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
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

	private final SchemaActions schemaActions;
	private final IndexingActions indexingActions;
	private final IndexingResponse indexingResponse;
	public static boolean pressedStop = false;
	private static final ThreadLocal<Thread> SINGLE_TASK = new ThreadLocal<>();

	@Override
	public ResponseEntity<IndexingResponse> indexingStart() {
		log.warn("Mapping /startIndexing executed");

		if (indexingActions.isIndexingActionsStarted())
			return indexingResponse.startFailed();

		Set<SiteEntity> siteEntities = schemaActions.fullInit();
		if (siteEntities.size() == 0)
			return indexingResponse.startFailedEmptyQuery();

		SINGLE_TASK.set(new Thread(() -> indexingActions.startFullIndexing(siteEntities), "0day-thread"));

		SINGLE_TASK.get().start();
		return indexingResponse.successfully();
	}

	@Override
	public ResponseEntity<IndexingResponse> indexingPageStart(String url) {

		log.warn("Mapping /indexPage executed");

		if (indexingActions.isIndexingActionsStarted())
			return indexingResponse.startFailed();

		if (url == null || url.equals(""))
			return indexingResponse.startFailedEmptyQuery();

		SiteEntity siteEntity = schemaActions.partialInit(url);
		if (siteEntity == null) return indexingResponse.indexPageFailed();
		SINGLE_TASK.set(new Thread(() -> indexingActions.startPartialIndexing(siteEntity), "0day-thread"));

		SINGLE_TASK.get().start();

		return indexingResponse.successfully();
	}

	@Override
	public ResponseEntity<IndexingResponse> indexingStop() {
		log.warn("Mapping /stopIndexing executed");

		if (!indexingActions.isIndexingActionsStarted())
			return indexingResponse.stopFailed();

		setPressedStop(true);
		indexingActions.setIndexingActionsStarted(false);

		return indexingResponse.successfully();
	}

	public void setPressedStop(boolean value) {
		pressedStop = value;
	}
}
