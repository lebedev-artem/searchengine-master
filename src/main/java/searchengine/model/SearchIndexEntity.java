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

	@ManyToOne(fetch = FetchType.LAZY, targetEntity = PageEntity.class, cascade = CascadeType.DETACH, optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "page_id_key"), columnDefinition = "Integer",
			referencedColumnName = "id", name = "page_id", nullable = false, updatable = false)
	private PageEntity pageEntity;

	@Column(name = "lemma_id", nullable = false)
	private int lemmaId;

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