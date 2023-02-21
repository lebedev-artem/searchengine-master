package searchengine.services.parsing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import searchengine.model.PageEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@RequiredArgsConstructor
public class TempStorage {
//	public volatile static Set<String> urls = ConcurrentHashMap.newKeySet();
//	public volatile static Set<String> paths = ConcurrentHashMap.newKeySet();
//	public volatile static Set<PageEntity> threadSafeUniquePages = ConcurrentHashMap.newKeySet();

	public static  volatile Integer count = 0;
//	public static volatile Set<String> urls = new HashSet<>();
	public static volatile Set<PageEntity> pages = ConcurrentHashMap.newKeySet(200);


	//	public static volatile Set<String> paths = new HashSet<>();

//	public static volatile HashMap<PageEntity, Integer> pageEntityHashMap = new HashMap<>();
}
