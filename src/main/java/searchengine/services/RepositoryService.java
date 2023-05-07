package searchengine.services;

import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RepositoryService {
	Long countLemmas();

	Long countPages();

	Long countIndexes();

	Long countSites();

	Integer countPagesFromSite(SiteEntity site);

	Integer countLemmasFromSite(SiteEntity site);

	List<SiteEntity> getSites();

	PageEntity getPageRef(Integer pageId);

	SiteEntity getSiteByUrl(String url);

	LemmaEntity getLemmaByNameFromSite(String name, SiteEntity site);

	IndexEntity getIndexByLemmaFromPage(LemmaEntity lemma, PageEntity page);

	Set<IndexEntity> getAllByLemma(LemmaEntity lemma);

	Boolean siteExistsWithUrl(String url);

	Boolean pageExistsOnSite(String path, SiteEntity site);

	void deleteLemmas();

	void deleteIndexes();

	void deleteSiteById(Integer Id);

	void deletePageById(Integer id);

	void deleteAllTables();

	void flushRepositories();

	void saveSite(SiteEntity site);

	void saveLemmas(Collection<LemmaEntity> lemmas);

	void saveIndexes(Collection<IndexEntity> indexes);

	void deleteSite(SiteEntity site);

	void deleteLemma(LemmaEntity lemma);

	void deletePages(List<PageEntity> pages);

	void deletePagesFromSite(SiteEntity site);

	void deleteLemmasFromSite(SiteEntity site);

	List<IndexEntity> getIndexesFromPages(List<PageEntity> pages);

	List<PageEntity> getPageFromSite(SiteEntity site, String path);

	List<PageEntity> getNextLevelPagesFromSite(SiteEntity site, String path);

	List<PageEntity> getPagesByIds(List<Integer> ids);


}
