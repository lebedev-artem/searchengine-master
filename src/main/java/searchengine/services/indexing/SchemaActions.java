package searchengine.services.indexing;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.IndexingStatus;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
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
@Slf4j
@Getter
@Setter
@NoArgsConstructor
@Component
public class SchemaActions {

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

		existingSiteEntities.forEach(siteEntity -> {
			if (sitesList.getSites().stream().noneMatch(s -> s.getUrl().equals(siteEntity.getUrl()))){
				siteRepository.deleteById(siteEntity.getId());
				log.warn(siteEntity.getName() + " " + siteEntity.getUrl() + " deleted from table");
			}
		});


		existingSiteEntities = siteRepository.findAll();
		//if existing sites is empty - virgin and go
		newSiteEntities = new HashSet<>();
		if (existingSiteEntities.size() == 0) {
			log.info("Table `site` is empty. All sites will be getting from SiteList");
			virginSchema();
			sitesList.getSites().forEach(site -> {
				newSiteEntities.add(initSiteRow(site));
			});
		} else {
			List<SiteEntity> finalExistingSiteEntities = existingSiteEntities;
			sitesList.getSites().forEach(newSite -> {
				//Search in DB each site from SiteList
				if (finalExistingSiteEntities.stream().anyMatch(existsSite -> existsSite.getUrl().equals(newSite.getUrl()))) {
					log.info("Site " + newSite.getName() + " " + newSite.getUrl() + " found in table");
					//If exists get SiteEntity from DB, change status and time, put to SET of new entities
					SiteEntity existingSiteEntity = siteRepository.findByUrl(newSite.getUrl());
					log.warn("Updating " + existingSiteEntity.getName() + " " + existingSiteEntity.getUrl() + " status and time");
					siteRepository.updateStatusStatusTimeByUrl(IndexingStatus.INDEXING.status, LocalDateTime.now(), existingSiteEntity.getUrl());
					//get all pages form site
//					Set<PageEntity> pageEntities = pageRepository.findAllBySiteEntity(existingSiteEntity);
					//Del all pages by Site
					pageRepository.deleteAllBySiteEntity(existingSiteEntity);
					lemmaRepository.deleteAllBySiteEntity(existingSiteEntity);
					//decrease freq of lemmas
//					decreaseLemmasFreqByPage(pageEntities);
					newSiteEntities.add(existingSiteEntity);
					log.warn(existingSiteEntity.getName() + " " + existingSiteEntity.getUrl() + " will be indexing again");
					//Delete all pages, lemmas By SitEntity, index entries by PageEntities in one Query. CASCADE
				} else {
					newSiteEntities.add(initSiteRow(newSite));
					log.warn("NEW site " + newSite.getName() + " " + newSite.getUrl() + " added to indexing set");
				}
			});
		}
		newSiteEntities.forEach(e -> {
			if (!siteRepository.existsByUrl(e.getUrl())) siteRepository.save(e);
		});
		log.info("Schema initialized");
		return newSiteEntities;
	}

	public Set<SiteEntity> partialInit(HttpServletRequest request) {

		return new HashSet<>();
	}

	private @NotNull SiteEntity initSiteRow(@NotNull Site site) {
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setStatus(IndexingStatus.INDEXING.status);
		siteEntity.setStatusTime(LocalDateTime.now());
		siteEntity.setLastError("");
		siteEntity.setUrl(site.getUrl());
		siteEntity.setName(site.getName());
		return siteEntity;
	}

	private void virginSchema() {
		indexRepository.deleteAllInBatch();
		lemmaRepository.deleteAllInBatch();
		pageRepository.deleteAllInBatch();
		siteRepository.deleteAllInBatch();
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

	private void decreaseLemmasFreqByPage(Set<PageEntity> pageEntities) {
		log.warn("Start decreasing freq of lemmas by deleted pages");
		pageEntities.forEach(pageEntity -> {
			Set<Integer> lemmaIdxByPageId = lemmaRepository.findAllLemmaIdByPageId(pageEntity.getId());
			pageRepository.delete(pageEntity);
			lemmaIdxByPageId.forEach(x -> {
				LemmaEntity lemmaEntity = lemmaRepository.findById(x);
				if (lemmaEntity != null) {
					int oldFreq = lemmaEntity.getFrequency();
					if (oldFreq == 1) lemmaRepository.delete(lemmaEntity);
					else lemmaEntity.setFrequency(oldFreq - 1);
				}
			});
		});
	}
}
