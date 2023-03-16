package searchengine.model;
import lombok.*;
import javax.persistence.Column;
import java.io.Serializable;

@Getter
@Setter
@Data
public class SearchIndexId implements Serializable {

	@Column(name = "page_id")
	private Long pageId;

	@Column(name = "lemma_id")
	private Long lemmaId;

	public SearchIndexId(Long pageId, Long lemmaId) {
		this.pageId = pageId;
		this.lemmaId = lemmaId;
	}

	public SearchIndexId() {
	}
}
