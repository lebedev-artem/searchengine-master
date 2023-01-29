package searchengine.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import searchengine.services.interfaces.IndexService;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class ShutdownService {
	private static final Logger logger = LogManager.getLogger(ShutdownService.class);

	public void stop(ExecutorService pool){
		pool.shutdown();
		try {
			if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
				pool.shutdownNow();
				if (!pool.awaitTermination(10, TimeUnit.SECONDS))
					logger.warn("Pool " + pool.getClass().getName() +  " did not terminate");
			}
		} catch (InterruptedException ie) {
			pool.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
