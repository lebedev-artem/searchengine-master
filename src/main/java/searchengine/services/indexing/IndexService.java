package searchengine.services.indexing;
import org.springframework.http.ResponseEntity;
import searchengine.model.SiteEntity;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public interface IndexService{

	ResponseEntity<?> indexingStart(Set<SiteEntity> siteEntities) throws Exception;
	ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException;
	ResponseEntity<?> indexingPageStart(SiteEntity siteEntity) throws Exception;

	void setPressedStop(boolean value);

}
