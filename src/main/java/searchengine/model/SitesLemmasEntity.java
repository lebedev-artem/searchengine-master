package searchengine.model;
import javax.persistence.*;

@Entity
@Table(name = "sites_lemmas")
public class SitesLemmasEntity {

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
