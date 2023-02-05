package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import searchengine.config.SitesList;

import java.util.concurrent.ExecutionException;

public interface IndexService{
	public boolean isStarted = false;

	ResponseEntity<?> indexingStart(SitesList site) throws Exception;

	ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException;



}
