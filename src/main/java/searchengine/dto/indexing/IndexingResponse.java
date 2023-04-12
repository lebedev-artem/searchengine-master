package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Component
public class IndexingResponse {
	private boolean result;
	private String error;

	public ResponseEntity<IndexingResponse> successfully() {
		return new ResponseEntity<>(
				new IndexingResponse(
						true,
						""),
				HttpStatus.OK);
	}

	public ResponseEntity<IndexingResponse> startFailed() {
		return new ResponseEntity<>(
				new IndexingResponse(
						false,
						"Индексация уже запущена"),
				HttpStatus.BAD_REQUEST);
	}

	public ResponseEntity<IndexingResponse> stopFailed() {
		return new ResponseEntity<>(
				new IndexingResponse(
						false,
						"Индексация не запущена"),
				HttpStatus.BAD_REQUEST);
	}

	public ResponseEntity<IndexingResponse> startFailedEmptyQuery() {
		return new ResponseEntity<>(
				new IndexingResponse(
						false,
						"Индексацию запустить не удалось. Пустой поисковый запрос или список сайтов"),
				HttpStatus.BAD_REQUEST);
	}

	public ResponseEntity<IndexingResponse> indexPageFailed() {
		return new ResponseEntity<>(
				new IndexingResponse(false,
						"Данная страница находится за пределами сайтов, " +
								"указанных в конфигурационном файле"),
				HttpStatus.NOT_FOUND);
	}
}
