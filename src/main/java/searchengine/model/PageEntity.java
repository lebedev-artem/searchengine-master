package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@NoArgsConstructor
@Table(name = "page", indexes = @Index(name = "path_siteId_index", columnList = "path, site_id", unique = true))
public class PageEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Column(nullable = false)
	private Integer id;

	//	cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.REFRESH}
	@ManyToOne(fetch = FetchType.LAZY, targetEntity = SiteEntity.class, cascade = {CascadeType.MERGE, CascadeType.REFRESH}, optional = false)
	@OnDelete(action = OnDeleteAction.NO_ACTION)
	@JoinColumn(foreignKey = @ForeignKey(name = "site_page_FK"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = false, updatable = false)
	private SiteEntity siteEntity;

	/*
	https://www.navicat.com/en/company/aboutus/blog/1308-choosing-between-varchar-and-text-in-mysql#:~:text=Some%20Differences%20Between%20VARCHAR%20and%20TEXT&text=The%20VAR%20in%20VARCHAR%20means,be%20part%20of%20an%20index.
	 The max length of a varchar is subject to the max row size in MySQL, which is 64KB (not counting BLOBs):
	VARCHAR(65535)

	However, note that the limit is lower if you use a multi-byte character set:
	VARCHAR(21844) CHARACTER SET utf8

	15.22 InnoDB Limits
	https://dev.mysql.com/doc/refman/8.0/en/innodb-limits.html

	 */

	@Column(name = "path", columnDefinition = "VARCHAR(768) CHARACTER SET utf8")
//	@Column(name = "path", columnDefinition = "TEXT NOT NULL, KEY path_index (path(255))")
	private String path;

	@Column(nullable = false)
	private int code;

	@Column(length = 16777215, columnDefinition = "mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", nullable = false)
	private String content;

	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
//	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinTable(
			name = "search_index",
			joinColumns = {@JoinColumn(name = "page_id")},
			inverseJoinColumns = {@JoinColumn(name = "lemma_id")})
//	@ManyToMany(fetch = FetchType.LAZY, mappedBy = "pageEntities", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.REFRESH})
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<LemmaEntity> lemmaEntities = new HashSet<>();

	public PageEntity(SiteEntity siteEntity, int code, String content, String path) {
		this.siteEntity = siteEntity;
		this.path = path;
		this.code = code;
		this.content = content;
	}
}