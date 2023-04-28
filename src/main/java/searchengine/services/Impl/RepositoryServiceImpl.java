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

import java.util.Collection;
import java.util.List;
import java.util.Set;

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
	public Integer countPagesFromSite(SiteEntity site) {
		return pageRepository.countBySiteEntity(site);
	}

	@Override
	public Integer countLemmasFromSite(SiteEntity site) {
		return lemmaRepository.countBySiteEntity(site);
	}

	@Override
	public void flushRepositories() {
		lemmaRepository.flush();
		pageRepository.flush();
		indexRepository.flush();
		siteRepository.flush();
	}

	@Override
	public void saveSite(SiteEntity site) {
		siteRepository.save(site);
	}

	@Override
	public void saveLemmas(Collection<LemmaEntity> lemmas) {
		lemmaRepository.saveAll(lemmas);
	}

	@Override
	public void saveIndexes(Collection<IndexEntity> indexes) {
		indexRepository.saveAll(indexes);
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
	public PageEntity getPageRef(Integer pageId) {
		return pageRepository.getReferenceById(pageId);
	}

	@Override
	public SiteEntity getSiteByUrl(String url) {
		return siteRepository.findByUrl(url);
	}

	@Override
	public LemmaEntity getLemmaByNameFromSite(String name, SiteEntity site) {
		return lemmaRepository.findByLemmaAndSiteEntity(name, site);
	}

	@Override
	public IndexEntity getIndexByLemmaFromPage(LemmaEntity lemma, PageEntity page) {
		return indexRepository.findByLemmaEntityAndPageEntity(lemma, page);
	}

	@Override
	public Set<IndexEntity> getAllByLemma(LemmaEntity lemma) {
		return indexRepository.findAllByLemmaEntity(lemma);
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
	public List<PageEntity> getPagesByIds(List<Integer> ids) {
		return pageRepository.findAllByIdIn(ids);
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
