package searchengine.model;

import lombok.*;
import org.hibernate.annotations.Immutable;
import javax.persistence.*;

@Setter
@Getter
@Entity
@Immutable
@NoArgsConstructor
@Table(name = "search_index")
public class IndexEntity {

	@Id
	@Column(nullable = false)
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_index_page_id"), name = "page_id", nullable = false)
	public PageEntity pageEntity;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_index_lemma_id"), name = "lemma_id", nullable = false)
	public LemmaEntity lemmaEntity;

	@Column(name = "lemma_rank", nullable = false)
	private float lemmaRank;

	public IndexEntity(PageEntity pageEntity, LemmaEntity lemmaEntity, float lemmaRank) {
		this.pageEntity = pageEntity;
		this.lemmaEntity = lemmaEntity;
		this.lemmaRank = lemmaRank;
	}
}