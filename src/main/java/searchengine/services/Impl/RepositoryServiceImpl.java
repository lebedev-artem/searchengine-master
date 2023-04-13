package searchengine.services.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.RepositoryService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RepositoryServiceImpl implements RepositoryService {

	private final PageRepository pageRepository;
	private final SiteRepository siteRepository;
	private final IndexRepository indexRepository;
	private final LemmaRepository lemmaRepository;

	@Override
	public Long countLemmas() {
		return lemmaRepository.count();
	}

	@Override
	public Long countPages() {
		return pageRepository.count();
	}

	@Override
	public Long countIndexes() {
		return indexRepository.count();
	}

	@Override
	public Long countSites() {
		return siteRepository.count();
	}

	@Override
	public void flushRepositories() {
		lemmaRepository.flush();
		pageRepository.flush();
		indexRepository.flush();
		siteRepository.flush();
	}

	@Override
	public Integer countPagesOnSite(SiteEntity site) {
		return pageRepository.countBySiteEntity(site);
	}

	@Override
	public void saveSite(SiteEntity site) {
		siteRepository.save(site);
	}

	@Override
	public void deleteSite(SiteEntity site) {
		siteRepository.delete(site);
	}

	@Override
	public List<SiteEntity> getSites() {
		return siteRepository.findAll();
	}

	@Override
	public SiteEntity getSiteByUrl(String url) {
		return siteRepository.findByUrl(url);
	}

	@Override
	public Boolean siteExistsWithUrl(String url) {
		return siteRepository.existsByUrl(url);
	}

	@Override
	public void deleteAllTables() {
		indexRepository.deleteAllInBatch();
		lemmaRepository.deleteAllInBatch();
		pageRepository.deleteAllInBatch();
		siteRepository.deleteAllInBatch();
	}

	@Override
	public void deletePagesFromSite(SiteEntity site) {
		pageRepository.deleteAllBySiteEntity(site);
	}

	@Override
	public Boolean pageExistsOnSite(String path, SiteEntity site) {
		return pageRepository.existsByPathAndSiteEntity(path, site);
	}

	@Override
	public List<PageEntity> getNextLevelPagesFromSite(SiteEntity site, String path) {
		return pageRepository.findAllBySiteEntityAndPathContains(site, path);
	}

	@Override
	public List<PageEntity> getPageFromSite(SiteEntity site, String path) {
		return pageRepository.findBySiteEntityAndPath(site, path);
	}

	@Override
	public void deletePages(List<PageEntity> pages) {
		pageRepository.deleteAllInBatch(pages);
	}

	@Override
	public void deleteLemmasFromSite(SiteEntity site) {
		lemmaRepository.deleteAllInBatchBySiteEntity(site);
	}

	@Override
	public void deleteLemmas() {
		lemmaRepository.deleteAllInBatch();
	}

	@Override
	public void deleteLemma(LemmaEntity lemma) {
		lemmaRepository.delete(lemma);
	}

	@Override
	public void deleteIndexes() {
		indexRepository.deleteAllInBatch();
	}

	@Override
	public List<IndexEntity> getIndexesFromPages(List<PageEntity> pages) {
		return indexRepository.findAllByPageEntityIn(pages);
	}


}
