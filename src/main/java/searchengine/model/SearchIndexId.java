package searchengine.model;
import lombok.*;
import javax.persistence.Column;
import java.io.Serializable;

@Getter
@Setter
@Data
public class SearchIndexId implements Serializable {

	@Column(name = "page_id")
	private Integer page_id;

	@Column(name = "lemma_id")
	private Integer lemma_id;

	public SearchIndexId(Integer page_id, Integer lemma_id) {
		this.page_id = page_id;
		this.lemma_id = lemma_id;
	}

	public SearchIndexId() {
	}
}
