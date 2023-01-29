package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

@Service
public interface IndexService{

	public ResponseEntity<?> indexingStart(SitesList site) throws Exception;

	public void indexingStop() throws ExecutionException, InterruptedException;


}
