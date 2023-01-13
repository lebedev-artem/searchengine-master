package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
@Setter
@Getter
@Entity
@Table(name = "lemma")
public class Lemma {
	@Id
	@Column(nullable = false)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@ManyToOne(fetch = FetchType.LAZY, targetEntity = Site.class, cascade = CascadeType.DETACH, optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "site_id_key_lemma"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "site_id", nullable = false, updatable = false)
//	@Column(nullable = false, name = "site_id")
	private Site site;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String lemma;

	@Column(nullable = false)
	private int frequency;
}

/*
lemma — леммы, встречающиеся в текстах (см. справочно:
лемматизация).
● id INT NOT NULL AUTO_INCREMENT;
● site_id INT NOT NULL — ID веб-сайта из таблицы site;
● lemma VARCHAR(255) NOT NULL — нормальная форма слова (лемма);
● frequency INT NOT NULL — количество страниц, на которых слово
встречается хотя бы один раз. Максимальное значение не может
превышать общее количество слов на сайте.
 */