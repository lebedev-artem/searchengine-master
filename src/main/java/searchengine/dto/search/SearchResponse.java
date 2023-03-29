package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SearchResponse {
	private boolean result;
	private String error;
	private Integer count;
	private List<SearchData> data;

	public SearchResponse(boolean result, String error) {
		this.result = result;
		this.error = error;
	}

	public ResponseEntity<SearchResponse> emptyQuery() {
		return new ResponseEntity<>(new SearchResponse(false, "Empty query"), HttpStatus.BAD_REQUEST);
	}
}
