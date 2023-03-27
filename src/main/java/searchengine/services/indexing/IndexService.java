package searchengine.services.indexing;
import org.springframework.http.ResponseEntity;
import searchengine.model.SiteEntity;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public interface IndexService{

	ResponseEntity<?> indexingStart(Set<SiteEntity> siteEntities) throws Exception;
	ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException;
	ResponseEntity<?> indexingPageStart(SiteEntity siteEntity) throws Exception;

	void test(Integer id);
	void setPressedStop(boolean value);

}
