package searchengine.model;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
@Getter
@Setter
@Entity
@Table(name = "sites_lemmas")
public class SitesLemmasEntity implements BaseEntity{
//	private Boolean deleted;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private int id;

	@ManyToOne
	@JoinColumn(name = "lemma_id", insertable = false, updatable = false)
	private LemmaEntity lemmaEntity;

	@ManyToOne
	@JoinColumn(name = "site_id", insertable = false, updatable = false)
	private SiteEntity siteEntity;
}
