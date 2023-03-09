package searchengine.services.interfaces;
import org.springframework.http.ResponseEntity;
import searchengine.config.Site;
import searchengine.config.SitesList;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ExecutionException;

public interface IndexService{

	ResponseEntity<?> indexingStart(SitesList site) throws Exception;
	ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException;
	ResponseEntity<?> indexingPageStart(HttpServletRequest request) throws Exception;
//	ResponseEntity<?> testDeleteSiteWithPages(String name) throws ExecutionException, InterruptedException;
	void startSavingPagesService();
	void startLemmasIndexFinder(Site site);

	Boolean isAllowed();

}
