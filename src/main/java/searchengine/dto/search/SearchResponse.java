package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SearchResponse {
	private boolean result;
	private String error;
	private Integer count;
	private List<SearchData> data;

	public SearchResponse(boolean result, String error) {
		this.result = result;
		this.error = error;
	}
}
