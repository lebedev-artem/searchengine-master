package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "lemma")
public class LemmaEntity {

	@Id
	@Column(nullable = false)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
//	@ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.REFRESH})
	@OnDelete(action = OnDeleteAction.NO_ACTION)
	@JoinColumn(foreignKey = @ForeignKey(name = "lemma_site_FK"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = false, updatable = false)
	private SiteEntity siteEntity;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String lemma;

	@Column(nullable = false)
	private int frequency;

	@ManyToMany(cascade = {CascadeType.ALL})
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinTable(
			name = "search_index",
			joinColumns = {@JoinColumn(name = "lemma_id")},
			inverseJoinColumns = {@JoinColumn(name = "page_id")}
	)
	private Set<PageEntity> pageEntities = new HashSet<>();


	public LemmaEntity(SiteEntity siteEntity, String lemma, int frequency) {
		this.siteEntity = siteEntity;
		this.lemma = lemma;
		this.frequency = frequency;
	}

	public LemmaEntity() {
	}

}