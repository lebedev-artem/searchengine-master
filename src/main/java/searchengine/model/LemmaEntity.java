package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "lemma")
public class LemmaEntity implements BaseEntity {

	public LemmaEntity(SiteEntity siteEntity, String lemma, int frequency) {
		this.siteEntity = siteEntity;
		this.lemma = lemma;
		this.frequency = frequency;
	}

	public LemmaEntity() {
	}

	@Id
	@Column(nullable = false)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

//	@ManyToOne(mappedBy = "lemmaEntity")
	@ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.REFRESH})
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(foreignKey = @ForeignKey(name = "lemma_site_FK"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = false, updatable = false)
	private SiteEntity siteEntity;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String lemma;

	@Column(nullable = false)
	private int frequency;

	@OneToOne(mappedBy = "lemmaEntity")
	private SearchIndexEntity searchIndexEntity;

}