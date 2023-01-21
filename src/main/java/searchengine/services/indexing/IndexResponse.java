package searchengine.services.indexing;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IndexResponse{
	private boolean result;
	private String error;

	public IndexResponse(boolean b, String s) {
		this.result = b;
		this.error = s;
	}
}
