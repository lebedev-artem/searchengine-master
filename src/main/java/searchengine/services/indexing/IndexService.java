package searchengine.services.indexing;

import org.springframework.stereotype.Service;
import searchengine.config.Site;

@Service
public interface IndexService{

	public Runnable indexingStart(Site site);

	public void indexingStop();

}
