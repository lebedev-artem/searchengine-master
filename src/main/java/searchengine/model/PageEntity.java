package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;

@Setter
@Getter
@Entity

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

//	@ManyToOne(fetch = FetchType.LAZY, targetEntity = SiteEntity.class, cascade = CascadeType.DETACH, optional = false)
	@ManyToOne(fetch = FetchType.LAZY, targetEntity = SiteEntity.class, cascade = CascadeType.MERGE, optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "site_id_key_page"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = true, updatable = false)
	private SiteEntity siteEntity;

	@Column(name = "path", columnDefinition = "TEXT NOT NULL, KEY path_index (path(255))")
	private String path;

	@Column(nullable = false)
	private int code;

	@Column(length = 16777215, columnDefinition = "mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", nullable = true)
	private String content;

//	@OneToMany(mappedBy = "pageEntity")
//	private Set<SearchIndexEntity> searchIndexEntities;

	@OneToOne(mappedBy = "pageEntity")
	private SearchIndexEntity searchIndexEntity;
}