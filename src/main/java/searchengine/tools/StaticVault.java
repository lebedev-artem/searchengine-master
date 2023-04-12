package searchengine.tools;

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

	public static Map<String, LemmaEntity> lemmaEntitiesMap = new HashMap<>();
	public static Set<IndexEntity> indexEntitiesMap = new HashSet<>();

}
