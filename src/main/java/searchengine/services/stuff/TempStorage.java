package searchengine.services.stuff;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import searchengine.model.PageEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@RequiredArgsConstructor
public class TempStorage {

	public static String siteUrl = "";
	public static volatile Integer nowOnMapPages = 0;
	public static volatile Set<PageEntity> pages = ConcurrentHashMap.newKeySet(35);

}
