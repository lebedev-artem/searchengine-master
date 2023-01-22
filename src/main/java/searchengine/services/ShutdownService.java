package searchengine.services;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class ShutdownService {

	public void stop(ExecutorService pool){
		pool.shutdown();
		try {
			if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
				pool.shutdownNow();
				if (!pool.awaitTermination(10, TimeUnit.SECONDS))
					System.err.println("Pool did not terminate");
			}
		} catch (InterruptedException ie) {
			pool.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
