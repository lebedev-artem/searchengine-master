package searchengine.services.indexing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndexResponse {
	private boolean result;
	private String error;

	public ResponseEntity<IndexResponse> successfully() {
		return new ResponseEntity<>(new IndexResponse(true, ""), HttpStatus.OK);
	}

	public ResponseEntity<IndexResponse> startFailed() {
		return new ResponseEntity<>(new IndexResponse(false, "Индексация уже запущена"), HttpStatus.BAD_REQUEST);
	}

	public ResponseEntity<IndexResponse> stopFailed() {
		return new ResponseEntity<>(new IndexResponse(false, "Индексация не запущена"), HttpStatus.BAD_REQUEST);
	}

	public ResponseEntity<IndexResponse> indexPageFailed() {
		return new ResponseEntity<>(new IndexResponse(false, "Данная страница находится за пределами сайтов, " +
				"указанных в конфигурационном файле"), HttpStatus.NOT_FOUND);
	}
}
