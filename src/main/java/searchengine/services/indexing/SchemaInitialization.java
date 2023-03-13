package searchengine.services.indexing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Component
public class SchemaInitialization {

	private IndexingMode mode;

	@Autowired
	SitesList sitesList;
	@Autowired
	SiteRepository siteRepository;
	@Autowired
	PageRepository pageRepository;
	@Autowired
	LemmaRepository lemmaRepository;
	@Autowired
	SearchIndexRepository searchIndexRepository;

	private void initSchema() {
		switch (mode) {
			case FULL -> fullInit();
			case PARTIAL -> partialInit();
		}
	}

	public @NotNull Set<SiteEntity> fullInit(){
		siteRepository.deleteAllInBatch();
		searchIndexRepository.resetIdOnIndexTable();
		lemmaRepository.resetIdOnLemmaTable();
		pageRepository.resetIdOnPageTable();
		siteRepository.resetIdOnSiteTable();
		Set<SiteEntity> siteEntities = new HashSet<>();
		for (Site s : sitesList.getSites()) {
			siteEntities.add(initSiteRow(s));
		}
		return siteEntities;
	}

	public Set<SiteEntity> partialInit(){

		return new HashSet<>();
	}

	private @NotNull SiteEntity initSiteRow(@NotNull Site site) {
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setStatus("INDEXING");
		siteEntity.setStatusTime(LocalDateTime.now());
		siteEntity.setLastError("");
		siteEntity.setUrl(site.getUrl());
		siteEntity.setName(site.getName());
		return siteEntity;
	}
}
