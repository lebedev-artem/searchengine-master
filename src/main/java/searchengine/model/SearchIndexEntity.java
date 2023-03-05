package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Setter
@Getter
@Entity
@Table(name = "search_index")
public class SearchIndexEntity implements BaseEntity{

	@EmbeddedId
	private SearchIndexId id;

	@ManyToOne
	@MapsId("pageId")
	@JoinColumn(name = "page_id")
	private PageEntity pageEntity;

	@ManyToOne
	@MapsId("lemmaId")
	@JoinColumn(name = "lemma_id")
	private LemmaEntity lemmaEntity;

	@Column(name = "lemma_rank", nullable = false)
	private float lemmaRank;

	public SearchIndexEntity(PageEntity pageEntity, LemmaEntity lemmaEntity, float lemmaRank) {
		this.pageEntity = pageEntity;
		this.lemmaEntity = lemmaEntity;
		this.lemmaRank = lemmaRank;
	}
}