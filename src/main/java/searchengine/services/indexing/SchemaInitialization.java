package searchengine.services.indexing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.IndexingStatus;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Component
public class SchemaInitialization {

//	private IndexingMode mode;
	private static final Logger rootLogger = LogManager.getRootLogger();
	Set<SiteEntity> newSiteEntities = new HashSet<>();

	@Autowired
	SitesList sitesList;
	@Autowired
	SiteRepository siteRepository;
	@Autowired
	PageRepository pageRepository;
	@Autowired
	LemmaRepository lemmaRepository;
	@Autowired
	IndexRepository indexRepository;

//	public Set<SiteEntity> initSchema(String mode) {
//		switch (mode) {
//			case "FULL" -> newSiteEntities = fullInit();
//			case "PARTIAL" -> newSiteEntities = partialInit();
//		}
//		return newSiteEntities;
//	}

	public @NotNull Set<SiteEntity> fullInit() {
		List<SiteEntity> existingSiteEntities = siteRepository.findAll();
		if (sitesList.getSites().size() == 0) return new HashSet<>();

		newSiteEntities = new HashSet<>();
		if (existingSiteEntities.size() == 0) {
			resetAllIds();
			sitesList.getSites().forEach(site -> {
				newSiteEntities.add(initSiteRow(site));
			});
		} else {
			sitesList.getSites().forEach(n -> {
				//Search in DB each site from SiteList
				if (existingSiteEntities.stream().anyMatch(e -> e.getUrl().equals(n.getUrl()))) {
					//If exists get SiteEntity from DB, change status and time, put to SET of new entities
					SiteEntity existingSiteEntity = siteRepository.findByUrl(n.getUrl());
					siteRepository.updateStatusStatusTimeByUrl(IndexingStatus.INDEXING.status, LocalDateTime.now(), existingSiteEntity.getUrl());
					newSiteEntities.add(existingSiteEntity);

					//Delete all pages, lemmas By SitEntity, index entries by PageEntities in one Query. CASCADE

				} else {
					newSiteEntities.add(initSiteRow(n));
				}
			});
		}
		newSiteEntities.forEach(e -> {
			if (!siteRepository.existsByUrl(e.getUrl())) siteRepository.save(e);
		});
		return newSiteEntities;
	}

	public Set<SiteEntity> partialInit(HttpServletRequest request) {

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

	private void virginSchema() {
		siteRepository.deleteAllInBatch();
		pageRepository.deleteAllInBatch();
		indexRepository.resetIdOnIndexTable();
		lemmaRepository.resetIdOnLemmaTable();
		pageRepository.resetIdOnPageTable();
		siteRepository.resetIdOnSiteTable();
	}

	private void resetAllIds() {

		indexRepository.resetIdOnIndexTable();
		lemmaRepository.resetIdOnLemmaTable();
		pageRepository.resetIdOnPageTable();
		siteRepository.resetIdOnSiteTable();
	}
}
