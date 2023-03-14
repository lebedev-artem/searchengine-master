package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Setter
@Getter
@Entity
@Table(name = "search_index")
public class SearchIndexEntity {

	@EmbeddedId
	private SearchIndexId id;

	@ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.REFRESH})
	@OnDelete(action = OnDeleteAction.CASCADE)
	@MapsId("pageId")
	@JoinColumn(name = "page_id")
	private PageEntity pageEntity;

	@ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.REFRESH})
	@OnDelete(action = OnDeleteAction.CASCADE)
	@MapsId("lemmaId")
	@JoinColumn(name = "lemma_id")
	private LemmaEntity lemmaEntity;

	@Column(name = "lemma_rank", nullable = false)
	private float lemmaRank;

	public SearchIndexEntity(PageEntity pageEntity, LemmaEntity lemmaEntity, float lemmaRank, SearchIndexId id) {
		this.pageEntity = pageEntity;
		this.lemmaEntity = lemmaEntity;
		this.lemmaRank = lemmaRank;
		this.id = id;
	}

	public SearchIndexEntity() {
	}
}