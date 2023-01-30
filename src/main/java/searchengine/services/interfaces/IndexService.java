package searchengine.services.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;

import java.util.concurrent.ExecutionException;

@Service
public interface IndexService{

	ResponseEntity<?> indexingStart(SitesList site) throws Exception;

	ResponseEntity<?> indexingStop() throws ExecutionException, InterruptedException;


}
