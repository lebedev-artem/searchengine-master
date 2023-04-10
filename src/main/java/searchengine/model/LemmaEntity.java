package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Setter
@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "lemma", indexes = @Index(name = "lemma_index", columnList = "lemma, site_id, id", unique = true))
public class LemmaEntity {

	@Id
	@Column(nullable = false)
	@SequenceGenerator(
			name = "lemma_seq",
			sequenceName = "lemma_sequence",
			initialValue = 1, allocationSize = 100)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lemma_seq")
	private Integer id;

	//	@ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
//	@ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.REFRESH})
	@ManyToOne(fetch = FetchType.LAZY)
	@OnDelete(action = OnDeleteAction.NO_ACTION)
	@JoinColumn(foreignKey = @ForeignKey(name = "lemma_site_FK"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = false, updatable = false)
	private SiteEntity siteEntity;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String lemma;

	@Column(nullable = false)
	private int frequency;

	//	@ManyToMany(cascade = {CascadeType.ALL})
//	@OnDelete(action = OnDeleteAction.CASCADE)
//	@JoinTable(
//			name = "search_index",
//			joinColumns = {@JoinColumn(name = "lemma_id")},
//			inverseJoinColumns = {@JoinColumn(name = "page_id")}
//	)
	@ManyToMany(fetch = FetchType.LAZY, mappedBy = "lemmaEntities", cascade = {CascadeType.MERGE, CascadeType.REFRESH})
	@OnDelete(action = OnDeleteAction.NO_ACTION)
	private Set<PageEntity> pageEntities = new HashSet<>();


	public LemmaEntity(SiteEntity siteEntity, String lemma, int frequency) {
		this.siteEntity = siteEntity;
		this.lemma = lemma;
		this.frequency = frequency;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LemmaEntity that = (LemmaEntity) o;
		return frequency == that.frequency && siteEntity.equals(that.siteEntity) && lemma.equals(that.lemma) && pageEntities.equals(that.pageEntities);
	}

	@Override
	public int hashCode() {
		return Objects.hash(siteEntity, lemma, frequency, pageEntities);
	}
}