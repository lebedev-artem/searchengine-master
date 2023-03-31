package searchengine.services.stuff;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@RequiredArgsConstructor
public class StaticVault {

//	public static String siteUrl = "";
//	public static volatile Integer nowOnMapPages = 0;
//	public static volatile Set<PageEntity> pages = ConcurrentHashMap.newKeySet(100);
	public static Map<String, LemmaEntity> lemmaEntitiesMap = new HashMap<>();
	public static Set<IndexEntity> indexEntitiesMap = new HashSet<>();
//	public static Set<String> skippedPaths = new HashSet<>();


//	public static Map<String, Integer> lemmaFreqMap = new HashMap<>();
//	public static Map<String, Integer> lemmaRankMap = new HashMap<>();
//	public static Map<LemmaEntity, Float> lemmasMap = new HashMap<>();
//	public static Set<SearchIndexEntity> indexEntitiesSet = new HashSet<>();

}
