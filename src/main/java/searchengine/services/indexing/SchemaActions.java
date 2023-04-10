package searchengine.services.indexing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
		//eSEx - existing in the database SiteEntities
		List<SiteEntity> eSEx = siteRepository.findAll();
		if (sitesList.getSites().size() == 0) return new HashSet<>();

		//Checking existing Site from DB on SiteList

		eSEx.forEach(siteEntity -> {
			if (sitesList.getSites().stream().noneMatch(s -> s.getUrl().equals(siteEntity.getUrl()))) {
				indexRepository.deleteAllInBatch();
				lemmaRepository.deleteAllInBatch();
				siteRepository.deleteById(siteEntity.getId());
				log.warn(siteEntity.getName() + " " + siteEntity.getUrl() + " deleted from table, because site not exist in SiteList");
			}
		});

		//nSEx - Set of newly created entities of Site
		Set<SiteEntity> nSEx = new HashSet<>();
		if (eSEx.size() == 0) {
			log.info("Table `site` is empty. All sites will be getting from SiteList");
			virginSchema();
			sitesList.getSites().forEach(site -> nSEx.add(initSiteRow(site)));
		} else {
			sitesList.getSites().forEach(newSite -> {
				SiteEntity existingSiteEntity = siteRepository.findByUrl(newSite.getUrl());
				if (existingSiteEntity != null) {
					log.info("Site " + newSite.getName() + " " + newSite.getUrl() + " found in table");
					log.warn("Updating " + existingSiteEntity.getName() + " " + existingSiteEntity.getUrl() + " status and time");
					siteRepository.updateStatusStatusTimeErrorByUrl("INDEXING", LocalDateTime.now(), " ", existingSiteEntity.getUrl());
					pageRepository.deleteAllBySiteEntity(existingSiteEntity);
					if (indexRepository.countBySiteId(existingSiteEntity.getId()) != null)
						deleteDetachedIndex(existingSiteEntity.getId());
					lemmaRepository.deleteAllBySiteEntity(existingSiteEntity);
					nSEx.add(existingSiteEntity);
					log.info(existingSiteEntity.getName() + " " + existingSiteEntity.getUrl() + " will be indexing again");
				} else {
					nSEx.add(initSiteRow(newSite));
					log.warn("NEW site " + newSite.getName() + " " + newSite.getUrl() + " added to indexing set");
				}
			});
		}
		nSEx.forEach(e -> {
			if (!siteRepository.existsByUrl(e.getUrl())) {
				siteRepository.save(e);
				log.warn("SiteEntity name " + e.getName() + " with URL " + e.getUrl() + " saved in table");
			}
		});

		if (pageRepository.count() == 0 & lemmaRepository.count() == 0 & indexRepository.count() == 0)
			resetAllIds();
		log.info("Schema initialized!");
		System.gc();
		return nSEx;
	}

	public SiteEntity partialInit(@NotNull HttpServletRequest request) {
		String url = request.getParameter("url");
		String path = "";
		try {
			path = new URL(url).getPath();
		} catch (MalformedURLException e) {
			log.error("Can't get path or hostname from request");
		}
		String hostName;
		if (path.equals("/")) {
			hostName = url.substring(0, url.lastIndexOf(path) + 1);
		} else {
			hostName = url.substring(0, url.indexOf(path) + 1);
		}

		Site site = null;
		for (Site s : sitesList.getSites()) {
			if (s.getUrl().toLowerCase(Locale.ROOT).equals(hostName.toLowerCase(Locale.ROOT))) {
				site = s;
			}
		}

		if (site == null) {
			log.error("SiteList doesn't contains hostname of requested URL");
			return null;
		}

		SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
		if (siteEntity == null) {
			log.warn("Table site doesn't contains entry with requested hostname");
			return null;
		}

		if (!pageRepository.existsByPathAndSiteEntity(path, siteEntity)) {
			log.warn("No pages with requested path contains in table page");
			log.warn("Will try indexing this URL now");
			siteEntity.setUrl(url);
			return siteEntity;
		}

		Set<String> pagesPaths = pageRepository.findPagesBySiteIdContainingPath(path, siteEntity.getId());
		Set<PageEntity> pageEntities = new HashSet<>();
		for (String p : pagesPaths) {
			pageEntities.add(pageRepository.findByPath(p));
		}

		log.info("Found " + pageEntities.size() + " entity(ies) in table page by requested path " + path);
		decreaseLemmasFreqByPage(pageEntities);
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

//		indexRepository.resetIdOnIndexTable();
//		lemmaRepository.resetIdOnLemmaTable();
//		pageRepository.resetIdOnPageTable();
//		siteRepository.resetIdOnSiteTable();
	}

	private void resetAllIds() {
		indexRepository.resetIdOnIndexTable();
		lemmaRepository.resetIdOnLemmaTable();
		pageRepository.resetIdOnPageTable();
		log.warn("All idx of tables reset now");
	}

	private void decreaseLemmasFreqByPage(@NotNull Set<PageEntity> pageEntities) {
		log.warn("Start decreasing freq of lemmas of deleted pages");
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

	private void deleteDetachedIndex(Integer siteId) {
		indexRepository.deleteBySiteId(siteId);
	}
}
