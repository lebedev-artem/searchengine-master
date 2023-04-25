package searchengine.tools;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Getter
@Setter
@Component
public class StringPool {
	public static Map<String, String> visitedLinks;
	public static Map<String, String> savedPaths = null;
	public static Map<String, String> pages404;


	public StringPool() {
		savedPaths = new ConcurrentHashMap<>(3000);
		visitedLinks = new ConcurrentHashMap<>(5000);
		pages404 = new ConcurrentHashMap<>(100);
	}

	public static String internVisitedLinks(String s){
		String exist = visitedLinks.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public static String internSavedPath(String s){
		String exist = savedPaths.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public static String internPage404(String s){
		String exist = pages404.putIfAbsent(s, s);
		return (exist == null) ? s : exist;
	}

	public static void clearAll(){
		savedPaths.clear();
		visitedLinks.clear();
		pages404.clear();
	}

}
