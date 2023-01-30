package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "lemma")
public class LemmaEntity implements BaseEntity {
	private Boolean deleted;

	@Id
	@Column(nullable = false)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@ManyToMany(mappedBy = "lemmaEntity")
	private Set<SiteEntity> siteEntity;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String lemma;

	@Column(nullable = false)
	private int frequency;

	@OneToOne(mappedBy = "lemmaEntity")
	private SearchIndexEntity searchIndexEntity;

}