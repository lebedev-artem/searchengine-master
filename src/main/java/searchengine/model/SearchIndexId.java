package searchengine.model;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.criteria.CriteriaBuilder;
import java.io.Serializable;

@Getter
@Setter
public class SearchIndexId implements Serializable {

	@Column(name = "page_id")
	private Integer pageId;

	@Column(name = "lemma_id")
	private Integer lemmaId;

	public SearchIndexId(Integer pageId, Integer lemmaId) {
		this.pageId = pageId;
		this.lemmaId = lemmaId;
	}
}
