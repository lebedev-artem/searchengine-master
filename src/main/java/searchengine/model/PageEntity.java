package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Setter
@Getter
@Entity

//@Table(name = "page", indexes = @Index(columnList = "site_id", unique = true))
@Table(name = "page")
public class PageEntity implements BaseEntity {
//	private Boolean deleted;

	public PageEntity() {
	}

	public PageEntity(SiteEntity siteEntity, int code, String content, String path) {
		this.siteEntity = siteEntity;
		this.path = path;
		this.code = code;
		this.content = content;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private int id;


	//	@ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
//	@JoinColumn(name = "site_id")
//	@OnDelete(action = OnDeleteAction.CASCADE)

	@ManyToOne(fetch = FetchType.LAZY, targetEntity = SiteEntity.class, cascade = CascadeType.REMOVE, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(foreignKey = @ForeignKey(name = "site_id_key_page"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = false, updatable = false)
	private SiteEntity siteEntity;

	@Column(name = "path", columnDefinition = "TEXT NOT NULL, KEY path_index (path(255))")
//	@Column(name = "path", columnDefinition = "TEXT (255) NOT NULL")
	private String path;

	@Column(nullable = false)
	private int code;

	@Column(length = 16777215, columnDefinition = "mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", nullable = false)
	private String content;

//	@OneToMany(mappedBy = "pageEntity")
//	private Set<SearchIndexEntity> searchIndexEntities;

	@OneToOne(mappedBy = "pageEntity")
	private SearchIndexEntity searchIndexEntity;
}