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
	private final Map<String, String> links;
	private final Map<String, String> paths;


	public StringPool() {
		paths = new ConcurrentHashMap<>(5000, 1);
		links = new ConcurrentHashMap<>(1, 1);
	}

	public String internLink(String s){
		String exist = links.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public String internPath(String s){
		String exist = paths.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public int linksSize() {
		return links.size();
	}

	public int pathsSize() {
		return paths.size();
	}
}
