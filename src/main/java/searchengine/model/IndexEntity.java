package searchengine.model;

import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Setter
@Getter
@Entity
@Immutable
//@AllArgsConstructor
@NoArgsConstructor
@Table(name = "search_index")
public class IndexEntity {

	@javax.persistence.Id
	@Column(nullable = false)
	@SequenceGenerator(
			name = "index_seq",
			sequenceName = "index_sequence",
			initialValue = 1, allocationSize = 500)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "index_seq")
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
//	@OnDelete(action = OnDeleteAction.CASCADE)
//	@MapsId(value = "pageId")
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_index_page_id"), name = "page_id", nullable = false)
	public PageEntity pageEntity;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
//	@OnDelete(action = OnDeleteAction.CASCADE)
//	@MapsId(("lemmaId"))
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_index_lemma_id"), name = "lemma_id", nullable = false)
	public LemmaEntity lemmaEntity;

	@Column(name = "lemma_rank", nullable = false)
	private float lemmaRank;

	public IndexEntity(PageEntity pageEntity, LemmaEntity lemmaEntity, float lemmaRank) {
		this.pageEntity = pageEntity;
		this.lemmaEntity = lemmaEntity;
		this.lemmaRank = lemmaRank;
	}

	//	@PrePersist
//	public void assignLemmaId() {
//		this.getId().setLemmaId(this.lemmaEntity.getId());
//	}
//
//	@Embeddable
//	@Getter
//	@Setter
//	@AllArgsConstructor
//	@NoArgsConstructor
//	@EqualsAndHashCode
//	public static class Id implements Serializable {

//		@Column(name = "page_id")
//		private Integer pageId;
//
//		@Column(name = "lemma_id", unique = true)
//		private Integer lemmaId;
//
//	}
//
//	@Override
//	public boolean equals(Object o) {
//		if (this == o) return true;
//		if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
//		IndexEntity indexEntity = (IndexEntity) o;
//		return getId() != null && Objects.equals(getId(), indexEntity.getId());
//	}
//
//	@Override
//	public int hashCode() {
//		int result = 17;
//		result = 31 * result + pageEntity.hashCode();
//		result = (int) (31 * result + lemmaRank);
//		result = 31 * result + (lemmaEntity.getLemma() != null ? lemmaEntity.getLemma().hashCode() : 0);
//		return result;
//	}

}