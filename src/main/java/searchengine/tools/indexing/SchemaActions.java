package searchengine.tools.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@Component
public class SchemaActions {

	private final SitesList sitesList;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final IndexRepository indexRepository;

	public @NotNull Set<SiteEntity> fullInit() {

		if (sitesList.getSites().size() == 0)
			return new HashSet<>();

		//exSE - existing in the database SiteEntities
		List<SiteEntity> exSE = siteRepository.findAll();

		//Checking existing Site from DB on SiteList
		deleteSiteIfNotExist(exSE);

		//newSE - Set of newly created entities of Site
		Set<SiteEntity> newSE = new HashSet<>();

		if (exSE.size() == 0) {
			log.warn("Table `site` is empty. All sites will be getting from SiteList");
			virginSchema();
			sitesList.getSites().forEach(site -> newSE.add(initSiteRow(site)));
		} else {
			sitesList.getSites().forEach(newSite -> {
				SiteEntity existingSiteEntity = siteRepository.findByUrl(newSite.getUrl());
				if (existingSiteEntity != null) {
					log.info("Site " + newSite.getName() + " " + newSite.getUrl() + " found in table");
					log.warn("Updating " + existingSiteEntity.getName() + " " + existingSiteEntity.getUrl() + " status and time");
					existingSiteEntity.setStatus(IndexingStatus.INDEXING);
					existingSiteEntity.setLastError("");
					existingSiteEntity.setStatusTime(LocalDateTime.now());

					log.warn("Deletion pages and lemmas with indexes from " + existingSiteEntity.getName() + " " + existingSiteEntity.getUrl());
					pageRepository.deleteAllBySiteEntity(existingSiteEntity);
					lemmaRepository.deleteAllInBatchBySiteEntity(existingSiteEntity);

					log.info("Site " + existingSiteEntity.getName() + " " + existingSiteEntity.getUrl() + " will be indexing again");
					newSE.add(existingSiteEntity);

				} else {
					log.warn("New site " + newSite.getName() + " " + newSite.getUrl() + " added to indexing set");
					newSE.add(initSiteRow(newSite));
				}
			});
		}
		newSE.forEach(e -> {
			if (!siteRepository.existsByUrl(e.getUrl())) {
				siteRepository.save(e);
				log.warn("SiteEntity name " + e.getName() + " with URL " + e.getUrl() + " saved in table");
			}
		});

		log.info("Schema initialized!");
		System.gc();
		return newSE;
	}

	private void deleteSiteIfNotExist(List<SiteEntity> exSE) {
		for (SiteEntity siteEntity: exSE) {
			if (sitesList.getSites()
					.stream()
					.noneMatch(site -> site.getUrl().equals(siteEntity.getUrl()))){
				indexRepository.deleteAllInBatch();
				lemmaRepository.deleteAllInBatch();
				siteRepository.deleteById(siteEntity.getId());
				log.warn(siteEntity.getName() + " " + siteEntity.getUrl() + " deleted from table, because site not exist in SiteList");
			}
		}
	}

	public SiteEntity partialInit(String url) {
		String path = "";
		try {
			path = new URL(url).getPath();
		} catch (MalformedURLException e) {
			log.error("Can't get path or hostname from requested url");
		}
		String hostName = url.substring(0, url.lastIndexOf(path) + 1);;

		Site site = null;
		for (Site s : sitesList.getSites()) {
			if (s.getUrl()
					.toLowerCase(Locale.ROOT)
					.equals(hostName.toLowerCase(Locale.ROOT))) {
				site = s;
				break;
			}
		}

		if (site == null) {
			log.error("SiteList doesn't contains hostname of requested URL");
			return null;
		}

		SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
		if (siteEntity == null) {
			log.error("Table site doesn't contains entry with requested hostname");
			return null;
		}

		if (!pageRepository.existsByPathAndSiteEntity(path, siteEntity)) {
			log.error("No pages with requested path contains in table page");
			log.info("Will try indexing this URL now");
			siteEntity.setUrl(url);
			return siteEntity;
		}

		List<PageEntity> pageEntities = pageRepository.findAllBySiteEntityAndPathContains(siteEntity, path);

		log.info("Found " + pageEntities.size() + " entity(ies) in table page by requested path " + path);
		decreaseLemmasFreqByPage(pageEntities);
		pageRepository.deleteAllInBatch(pageEntities);
		siteEntity.setUrl(url);
		log.info(siteEntity.getUrl() + " will be indexing now");

		return siteEntity;
	}

	private @NotNull SiteEntity initSiteRow(@NotNull Site site) {
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setStatus(IndexingStatus.INDEXING);
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
	}

	private void decreaseLemmasFreqByPage(@NotNull List<PageEntity> pageEntities) {
		log.warn("Start decreasing freq of lemmas of deleted pages");
		//getting all indexes by page

		//getting all lemmas by indexes
		List<IndexEntity> indexForLemmaDecreaseFreq = indexRepository.findAllByPageEntityIn(pageEntities);

		if (indexForLemmaDecreaseFreq == null){
			log.error("Set of Index entities by Page is empty");
			return;
		} else {
			log.info("Found " + indexForLemmaDecreaseFreq.size() + " index entities by set of requested pages");
		}

		for (IndexEntity indexObj : indexForLemmaDecreaseFreq) {

			LemmaEntity lemmaEntity = indexObj.getLemmaEntity();
			int oldFreq = lemmaEntity.getFrequency();

			if (oldFreq == 1) lemmaRepository.delete(lemmaEntity);
			else lemmaEntity.setFrequency(oldFreq - 1);
		}
	}

}
