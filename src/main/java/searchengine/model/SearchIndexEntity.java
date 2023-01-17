package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Setter
@Getter
@Entity
@Table(name = "search_index")
public class SearchIndexEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private int id;

	@OneToOne(fetch = FetchType.LAZY, targetEntity = PageEntity.class, cascade = CascadeType.DETACH, optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "page_id_key_search_index"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "page_id", nullable = false, updatable = false)
	private PageEntity pageEntity;


	@OneToOne(fetch = FetchType.LAZY, targetEntity = LemmaEntity.class, cascade = CascadeType.DETACH, optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "lemma_id_key_search_index"), columnDefinition = "Integer",
			name = "lemma_id", referencedColumnName = "id", nullable = false)
	private LemmaEntity lemmaEntity;

	@Column(name = "lemma_rank", nullable = false)
	private float lemmaRank;
}


/*
index — поисковый индекс
● id INT NOT NULL AUTO_INCREMENT;
● page_id INT NOT NULL — идентификатор страницы;
● lemma_id INT NOT NULL — идентификатор леммы;
● rank FLOAT NOT NULL — количество данной леммы для данной
страницы.

 */