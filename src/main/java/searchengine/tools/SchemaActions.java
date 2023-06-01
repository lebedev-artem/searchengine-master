package searchengine.tools;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
public class SchemaActions {

	private final SitesList sitesList;
	private final Environment environment;
	private final SiteRepository siteRepository;
	private final PageRepository pageRepository;
	private final LemmaRepository lemmaRepository;
	private final IndexRepository indexRepository;
	private final UrlFormatter urlFormatter = new UrlFormatter();


	public @NotNull Set<SiteEntity> fullInit() {
		Set<SiteEntity> newSites = new HashSet<>();

		if (sitesList.getSites().size() != 0) {
			List<SiteEntity> existingSites = siteRepository.findAll();

			if (Objects.equals(environment.getProperty("user-settings.clear-site-if-not-exists"), "true")) {
				deleteSiteIfNotExist(existingSites);
			}

			if (existingSites.size() == 0)
				virginSchema();

			sitesList.getSites().forEach(newSite -> newSites.add(getSiteEntity(newSite)));
			persistSites(newSites);
		}
		return newSites;
	}

	private @NotNull SiteEntity getSiteEntity(@NotNull Site newSite) {
		SiteEntity result;
		SiteEntity existingSite = siteRepository.findByUrl(newSite.getUrl());
		if (existingSite != null) {
			clearRelatedTables(existingSite);
			result = existingSite;
		} else {
			log.warn("New site " + newSite.getName() + " " + newSite.getUrl() + " added to indexing set");
			result = initSiteRow(newSite);
		}
		return result;
	}

	private void persistSites(@NotNull Set<SiteEntity> newSE) {
		newSE.forEach(e -> {
			if (!siteRepository.existsByUrl(e.getUrl())) {
				log.warn("SiteEntity name " + e.getName() + " with URL " + e.getUrl() + " saving in table");
				siteRepository.save(e);
			}
		});
		log.info("Schema initialized!");
	}

	private void clearRelatedTables(@NotNull SiteEntity site) {
		log.info("Site " + site.getName() + " " + site.getUrl() + " found in table");
		log.warn("Updating " + site.getName() + " " + site.getUrl() + " status and time");
		site.setStatus(IndexingStatus.INDEXING);
		site.setLastError("");
		site.setStatusTime(LocalDateTime.now());

		log.warn("Deletion pages and lemmas with indexes from " + site.getName() + " " + site.getUrl());
		pageRepository.deleteAllBySiteEntity(site);
		lemmaRepository.deleteAllInBatchBySiteEntity(site);

		log.info("Site " + site.getName() + " " + site.getUrl() + " will be indexing again");
	}

	private void deleteSiteIfNotExist(@NotNull List<SiteEntity> exSE) {
		for (SiteEntity siteEntity : exSE) {
			if (sitesList.getSites()
					.stream()
					.noneMatch(site -> site.getUrl().equals(siteEntity.getUrl()))) {
				log.warn("Try deleting " + siteEntity.getName() + " " + siteEntity.getUrl() + " from table, because site not exist in SiteList");
				siteRepository.delete(siteEntity);
			}
		}
	}

	public SiteEntity partialInit(@NotNull String url) {
		urlFormatter.setSitesList(sitesList);
		String path = "/" + url.replace(urlFormatter.getHomeSiteUrl(url), "");
		String hostName = url.substring(0, url.lastIndexOf(path) + 1);
		Site site = findSiteInConfig(hostName);
		SiteEntity siteEntity;

		siteEntity = checkExistingSite(site);
		if (siteEntity == null)
			return null;

		if (!pageRepository.existsByPathAndSiteEntity(path, siteEntity)) {
			log.error("No pages with requested path contains in table page");
			log.info("Will try indexing this URL now");
		} else {
			List<PageEntity> pageEntities = getPageEntities(path, siteEntity);
			log.info("Found " + pageEntities.size() + " entity(ies) in table page by requested path " + path);

			decreaseLemmasFreqByPage(pageEntities);
		}
		siteEntity.setUrl(url);
		log.info(siteEntity.getUrl() + " will be indexing now");
		return siteEntity;
	}

	@Nullable
	private SiteEntity checkExistingSite(Site site) {
		SiteEntity siteEntity;
		if (site == null) {
			log.error("SiteList doesn't contains hostname of requested URL");
			return null;
		} else {
			siteEntity = siteRepository.findByUrl(site.getUrl());
			if (siteEntity == null) {
				log.error("Table site doesn't contains entry with requested hostname");
				return null;
			}
		}
		return siteEntity;
	}

	private List<PageEntity> getPageEntities(String path, SiteEntity siteEntity) {
		List<PageEntity> pageEntities;
		if (Objects.equals(environment.getProperty("user-settings.delete-next-level-pages"), "true"))
			pageEntities = pageRepository.findAllBySiteEntityAndPathContains(siteEntity, path);
		else pageEntities = pageRepository.findBySiteEntityAndPath(siteEntity, path);
		return pageEntities;
	}

	private @Nullable Site findSiteInConfig(String hostName) {
		for (Site s : sitesList.getSites()) {
			if (s.getUrl()
					.toLowerCase(Locale.ROOT)
					.equals(hostName.toLowerCase(Locale.ROOT))) {
				return s;
			}
		}
		return null;
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
		log.warn("Table `site` is empty. All sites will be getting from SiteList");
		indexRepository.deleteAllInBatch();
		lemmaRepository.deleteAllInBatch();
		pageRepository.deleteAllInBatch();
		siteRepository.deleteAllInBatch();
	}

	private void decreaseLemmasFreqByPage(@NotNull List<PageEntity> pageEntities) {
		log.warn("Start decreasing freq of lemmas of deleted pages");

		List<IndexEntity> indexForLemmaDecreaseFreq = indexRepository.findAllByPageEntityIn(pageEntities);

		if (indexForLemmaDecreaseFreq == null) {
			log.error("Set of Index entities by Page is empty");
			return;
		}

		log.info("Found " + indexForLemmaDecreaseFreq.size() + " index entities by set of requested pages");
		pageRepository.deleteAllInBatch(pageEntities);

		for (IndexEntity indexObj : indexForLemmaDecreaseFreq) {
			LemmaEntity lemmaEntity = indexObj.getLemmaEntity();
			int oldFreq = lemmaEntity.getFrequency();

			if (oldFreq == 1) lemmaRepository.delete(lemmaEntity);
			else lemmaEntity.setFrequency(oldFreq - 1);
		}
	}

}
