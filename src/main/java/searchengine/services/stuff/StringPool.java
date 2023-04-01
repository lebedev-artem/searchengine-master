package searchengine.services.stuff;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Getter
@Setter
@Component
public class StringPool {
	public final Map<String, String> addedPathsToQueue;
	public final Map<String, String> savedPaths;
	public final Map<String, String> pages404;


	public StringPool() {
		savedPaths = new ConcurrentHashMap<>(5000, 1);
		addedPathsToQueue = new ConcurrentHashMap<>(5000, 1);
		pages404 = new ConcurrentHashMap<>(100);
	}

	public String internAddedPathToQueue(String s){
		String exist = addedPathsToQueue.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public String internSavedPath(String s){
		String exist = savedPaths.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public String internPage404(String s){
		String exist = pages404.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public int AddedPathToQueueSize() {
		return addedPathsToQueue.size();
	}

	public int pathsSize() {
		return savedPaths.size();
	}
}
