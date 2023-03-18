package searchengine.model;
import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Setter
@Getter
@Entity(name = "SiteEntity")
@Table(name = "site")
public class SiteEntity {

	public SiteEntity(String status, LocalDateTime statusTime, String lastError, String url, String name) {
		this.status = status;
		this.statusTime = statusTime;
		this.lastError = lastError;
		this.url = url;
		this.name = name;
	}

	public SiteEntity() {
	}

	@Id
	@Column (nullable = false)
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
	private String status;

	@Column(name = "status_time", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime statusTime;

	@Column(columnDefinition = "TEXT", name = "last_error", nullable = true, length = 500)
	private String lastError;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String url;

	@Column(columnDefinition = "VARCHAR(255)", nullable = false)
	private String name;

	@OneToMany(mappedBy = "siteEntity", cascade = CascadeType.REMOVE)
	private Set<PageEntity> pageEntities;

	@OneToMany(mappedBy = "siteEntity", cascade = CascadeType.REMOVE)
	private Set<LemmaEntity> lemmaEntity;
}
