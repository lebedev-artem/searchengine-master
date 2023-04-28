package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {

	ResponseEntity<IndexingResponse> indexingStop();

	ResponseEntity<IndexingResponse> indexingStart();

	ResponseEntity<IndexingResponse> indexingPageStart(String url);
}
