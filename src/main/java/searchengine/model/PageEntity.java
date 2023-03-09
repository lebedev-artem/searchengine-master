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
@Table(name = "page", indexes = @Index(name = "path_index", columnList = "path, site_id", unique = true))
public class PageEntity implements BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private int id;

	@ManyToOne(fetch = FetchType.LAZY, targetEntity = SiteEntity.class, cascade = CascadeType.REMOVE, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(foreignKey = @ForeignKey(name = "site_page_FK"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = false, updatable = false)
	private SiteEntity siteEntity;

	@Column(name = "path", columnDefinition = "TEXT (255) NOT NULL")
	private String path;

	@Column(nullable = false)
	private int code;

	@Column(length = 16777215, columnDefinition = "mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", nullable = false)
	private String content;

	@ManyToMany(mappedBy = "pageEntities")
	private Set<LemmaEntity> lemmaEntities = new HashSet<>();


	public PageEntity() {
	}

	public PageEntity(SiteEntity siteEntity, int code, String content, String path) {
		this.siteEntity = siteEntity;
		this.path = path;
		this.code = code;
		this.content = content;
	}
}