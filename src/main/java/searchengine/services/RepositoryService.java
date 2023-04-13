package searchengine.services;

import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

public interface RepositoryService {
	Long countLemmas();

	Long countPages();

	Long countIndexes();

	Long countSites();

	List<SiteEntity> getSites();

	SiteEntity getSiteByUrl(String url);

	Integer countPagesOnSite(SiteEntity site);

	Boolean siteExistsWithUrl(String url);

	Boolean pageExistsOnSite(String path, SiteEntity site);

	void deleteLemmas();

	void deleteIndexes();

	void deleteAllTables();

	void flushRepositories();

	void saveSite(SiteEntity site);

	void deleteSite(SiteEntity site);

	void deleteLemma(LemmaEntity lemma);

	void deletePages(List<PageEntity> pages);

	void deletePagesFromSite(SiteEntity site);

	void deleteLemmasFromSite(SiteEntity site);

	List<IndexEntity> getIndexesFromPages(List<PageEntity> pages);

	List<PageEntity> getPageFromSite(SiteEntity site, String path);

	List<PageEntity> getNextLevelPagesFromSite(SiteEntity site, String path);


}
