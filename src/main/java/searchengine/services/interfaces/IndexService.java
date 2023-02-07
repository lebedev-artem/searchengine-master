package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import searchengine.config.SitesList;

import java.util.concurrent.ExecutionException;

public interface IndexService{
	boolean isStarted = false;

	ResponseEntity<?> indexingStart(SitesList site) throws Exception;

	ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException;

	Boolean getStarted();

	Boolean getAllowed();

}
