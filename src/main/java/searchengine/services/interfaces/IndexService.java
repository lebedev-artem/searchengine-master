package searchengine.services.interfaces;
import org.springframework.http.ResponseEntity;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public interface IndexService{

	ResponseEntity<?> indexingStart(Set<SiteEntity> siteEntities) throws Exception;
	ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException;
	ResponseEntity<?> indexingPageStart(HttpServletRequest request) throws Exception;

	void startPagesSaver(SiteEntity siteEntity);
	void startLemmasCollector(SiteEntity siteEntity);
	void startIndexGenerator(SiteEntity siteEntity);

	Boolean isAllowed();
	void setIsRanOnce(boolean value);

}
