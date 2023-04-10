package searchengine.services.indexing;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repositories.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Setter
@Getter
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

	private final IndexResponse indexResponse;
	private final SiteRepository siteRepository;
	private final IndexingActions indexingActions;
	private final SchemaActions schemaActions;
	public static volatile boolean pressedStop = false;
	private static final ThreadLocal<Thread> singleTask = new ThreadLocal<>();

	@Override
	public synchronized ResponseEntity<?> indexingStart() {
		log.warn("Mapping /startIndexing executed");

		if (indexingActions.getIndexingActionsStarted())
			return indexResponse.startFailed();

		Set<SiteEntity> siteEntities = schemaActions.fullInit();
		if (siteEntities.size() == 0)
			return indexResponse.startFailedEmptySites();

		singleTask.set(new Thread(() -> {
			indexingActions.startFullIndexing(siteEntities);
		}, "0day-thread"));

		singleTask.get().start();
		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingPageStart(SiteEntity siteEntity) {

		if (indexingActions.getIndexingActionsStarted())
			return indexResponse.startFailed();

		singleTask.set(new Thread(() -> {
			indexingActions.startPartialIndexing(siteEntity);
		}, "0day-thread"));

		singleTask.get().start();

		return indexResponse.successfully();
	}

	@Override
	public ResponseEntity<?> indexingStop() {
		if (!indexingActions.getIndexingActionsStarted())
			return indexResponse.stopFailed();

		setPressedStop(true);
		indexingActions.setIndexingActionsStarted(false);

		siteRepository.updateAllStatusStatusTimeError("FAILED", LocalDateTime.now(), "Индексация остановлена пользователем");
		return indexResponse.successfully();
	}

	@Override
	public void setPressedStop(boolean value) {
		pressedStop = value;
	}
}