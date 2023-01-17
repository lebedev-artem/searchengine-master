package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;

@Setter
@Getter
@Entity
//@Table(name = "page", indexes = {@Index(name = "pathIndex", columnList = "path")})
@Table(name = "page")
public class PageEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private int id;

	@ManyToOne(fetch = FetchType.LAZY, targetEntity = SiteEntity.class, cascade = CascadeType.DETACH, optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "site_id_key_page"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = false, updatable = false)
//	@Column(name = "site_id", nullable = false)
	private SiteEntity siteEntity;

//	columnDefinition = "TEXT"
	@Column(name = "path", columnDefinition = "TEXT NOT NULL, KEY path_index (path(255))")
	private String path;

	@Column(nullable = false)
	private int code;

	@Column(columnDefinition = "MEDIUMTEXT CHARECTER SET utf8mb4 COLLATE utf8mb4_general_ci", nullable = false, length = 16777215)
	private String content;

	@OneToMany(mappedBy = "pageEntity")
	private List<SearchIndexEntity> searchIndexEntities;

	@OneToOne(mappedBy = "pageEntity")
	private SearchIndexEntity searchIndexEntity;
}

/*
page — проиндексированные страницы сайта
● id INT NOT NULL AUTO_INCREMENT;
● site_id INT NOT NULL — ID веб-сайта из таблицы site;
● path TEXT NOT NULL — адрес страницы от корня сайта (должен
начинаться со слэша, например: /news/372189/);
2
● code INT NOT NULL — код HTTP-ответа, полученный при запросе
страницы (например, 200, 404, 500 или другие);
● content MEDIUMTEXT NOT NULL — контент страницы (HTML-код).
По полю path должен быть установлен индекс, чтобы поиск по нему был
быстрым, когда в нём будет много ссылок. Индексы рассмотрены в курсе «Язык
запросов SQL».
 */