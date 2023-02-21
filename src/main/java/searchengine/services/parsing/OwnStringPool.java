package searchengine.services.parsing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Getter
@Setter
@Component
public class OwnStringPool  {
	private final Map<String, String> links;
	private final Map<String, String> paths;


	public OwnStringPool() {
		paths = new ConcurrentHashMap<>(5000, 1);
		links = new ConcurrentHashMap<>(5000, 1);
	}

	public String interLink(String s){
		String exist = links.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public String interPath(String s){
		String exist = paths.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public int internSizeLinks() {
		return links.size();
	}

	public int internSizePaths() {
		return paths.size();
	}
}
