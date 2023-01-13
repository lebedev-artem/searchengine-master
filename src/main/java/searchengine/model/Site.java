package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Date;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "site")
public class Site {
	@Id
	@Column (nullable = false)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
	private Status status;

	@Column(name = "status_time", nullable = false)
	private Date statusTime;

	@Column(columnDefinition = "TEXT", name = "last_error", nullable = true, length = 500)
	private String lastError;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String url;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String name;

	@OneToMany(mappedBy = "site")
	private List<Page> pages;

	@OneToMany(mappedBy = "site")
	private List<Lemma> lemmas;
}

/*
site — информация о сайтах и статусах их индексации
● id INT NOT NULL AUTO_INCREMENT;
● status ENUM('INDEXING', 'INDEXED', 'FAILED') NOT NULL — текущий
статус полной индексации сайта, отражающий готовность поискового
движка осуществлять поиск по сайту — индексация или переиндексация
в процессе, сайт полностью проиндексирован (готов к поиску) либо его не
удалось проиндексировать (сайт не готов к поиску и не будет до
устранения ошибок и перезапуска индексации);
● status_time DATETIME NOT NULL — дата и время статуса (в случае
статуса INDEXING дата и время должны обновляться регулярно при
добавлении каждой новой страницы в индекс);
● last_error TEXT — текст ошибки индексации или NULL, если её не было;
● url VARCHAR(255) NOT NULL — адрес главной страницы сайта;
● name VARCHAR(255) NOT NULL — имя сайта.
 */