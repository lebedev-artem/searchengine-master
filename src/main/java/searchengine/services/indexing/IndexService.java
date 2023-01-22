package searchengine.services.indexing;

import org.springframework.stereotype.Service;
import searchengine.config.Site;

import java.util.concurrent.ForkJoinPool;

@Service
public interface IndexService{

	public boolean indexingStart(Site site);

	public void indexingStop();

	public ForkJoinPool getPool();
}
