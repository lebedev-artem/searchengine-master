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
	private final Map<String, String> addedPathsToQueue;
	private final Map<String, String> paths;


	public StringPool() {
		paths = new ConcurrentHashMap<>(5000, 1);
		addedPathsToQueue = new ConcurrentHashMap<>(5000, 1);
	}

	public String internAddedPathToQueue(String s){
		String exist = addedPathsToQueue.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public String internPath(String s){
		String exist = paths.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public int AddedPathToQueueSize() {
		return addedPathsToQueue.size();
	}

	public int pathsSize() {
		return paths.size();
	}
}
