package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import searchengine.config.Site;
import searchengine.config.SitesList;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.ExecutionException;

public interface IndexService{
	boolean isStarted = false;

	ResponseEntity<?> indexingStart(SitesList site) throws Exception;

	ResponseEntity<?> singleIndexingStart(HttpServletRequest request) throws Exception;

	ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException;

	Boolean getStarted();

	Boolean getAllowed();

}
