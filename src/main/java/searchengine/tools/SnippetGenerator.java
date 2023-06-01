package searchengine.tools;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@Getter
@Setter
@RequiredArgsConstructor
public class SnippetGenerator {
	private String text;
	private List<String> queryWords;
	private final LemmaFinder lemmaFinder;
	private final Integer SNIPPET_LENGTH = 100;
	private final Integer MAX_FULL_SNIPPET_LENGTH = 500;

	public void setText(String text) {
		this.text = Jsoup
				.clean(text, Safelist.simpleText())
				.replaceAll("[^А-Яа-яЁё\\d\\s,.!]+", " ")
				.replaceAll("\\s+", " ");
	}

	public void setQueryWords(List<String> queryWords) {
		this.queryWords = queryWords;
	}

	public Map<Integer, String> getWordsAndPos(@NotNull String text) {
		Map<Integer, String> words = new HashMap<>();
		int pos = 0;
		int index = text.indexOf(" ");
		while (index >= 0) {
			String word = text.substring(pos, index);
			word = word.replaceAll("\\P{L}+", "");
			if (!word.isEmpty()) {
				words.put(pos, word);
			}
			pos = index + 1;
			index = text.indexOf(" ", pos);
		}
		String lastWord = text.substring(pos);
		lastWord = lastWord.replaceAll("\\P{L}+", "");
		if (!lastWord.isEmpty()) {
			words.put(pos, lastWord);
		}
		return words;
	}

	public Map<Integer, Set<String>> getLemmasAndPos() {
		Map<Integer, String> words = getWordsAndPos(text);
		Map<Integer, Set<String>> lemmas = new HashMap<>();
		for (Map.Entry<Integer, String> entry : words.entrySet()) {
			Set<String> lemmaSet = lemmaFinder.getLemmaSet(entry.getValue());
			if (!lemmaSet.isEmpty()) {
				lemmas.put(entry.getKey(), lemmaSet);
			}
		}
		return lemmas;
	}

	public Map<Integer, String> getDirtyFormsAndPos() {
		Map<Integer, String> dirtyForms = new TreeMap<>();
		Set<String> uniqueValues = new HashSet<>();
		for (String queryWord : queryWords) {
			for (Map.Entry<Integer, Set<String>> entry : getLemmasAndPos().entrySet()) {
				if (entry.getValue().contains(queryWord.toLowerCase())) {
					String word = getWordsAndPos(text).get(entry.getKey());
					dirtyForms.put(entry.getKey(), word);
				}
			}
		}

		for (Iterator<Map.Entry<Integer, String>> iterator = dirtyForms.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<Integer, String> entry = iterator.next();
			String value = entry.getValue();
			if (uniqueValues.contains(value)) {
				iterator.remove();
			} else {
				uniqueValues.add(value);
			}
		}

		return dirtyForms;
	}

	public String generateSnippets() {
		Map<Integer, String> dirtyForms = getDirtyFormsAndPos();
		List<Integer> sortedPositions = new ArrayList<>(dirtyForms.keySet());
		Collections.sort(sortedPositions);

		Map<String, Integer> snippets = cuttingSnippetFromText(dirtyForms, sortedPositions);

		Map<String, Integer> resultBoldedList = boldText(snippets, new ArrayList<>(dirtyForms.values()));
		StringBuilder sb = getResultSnippet(resultBoldedList);

		return sb.toString();
	}

	@NotNull
	private Map<String, Integer> cuttingSnippetFromText(Map<Integer, String> dirtyForms, @NotNull List<Integer> sortedPositions) {
		String prevSnippet = "";
		Map<String, Integer> snippets = new HashMap<>();
		int totalLength = 0;
		int prevPos = -1;
		int start = -1;

		for (Integer pos : sortedPositions) {
			if (prevPos == -1) {
				start = getLastDotPositionInText(pos);
			} else {
				int gap = pos - prevPos - dirtyForms.get(prevPos).length();
				if (gap > SNIPPET_LENGTH) {
					start = getLastDotPositionInText(pos);
					snippets.put(prevSnippet, 0);
				}
			}

			int end = Math.min(pos + dirtyForms.get(pos).length() + SNIPPET_LENGTH / 2, text.length());
			prevSnippet = text.substring(start, end);
			totalLength = totalLength + prevSnippet.length();

			if (totalLength >= MAX_FULL_SNIPPET_LENGTH) {
				break;
			}
			prevPos = pos;
		}

		if (!prevSnippet.isEmpty()) {
			snippets.put(prevSnippet, 0);
		}
		return snippets;
	}

	private @NotNull StringBuilder getResultSnippet(@NotNull Map<String, Integer> resultBoldedList) {
		StringBuilder sb = new StringBuilder();
		for (String s : resultBoldedList.keySet()) {
			sb.append("&#8195").append(s).append(" . . .").append("<br><br>");
			if (sb.length() >= MAX_FULL_SNIPPET_LENGTH) {
				break;
			}
		}
		return sb;
	}

	private int getLastDotPositionInText(Integer pos) {
		int lastDotPosition = text.substring(0, pos).lastIndexOf(".") + 2;
		if (lastDotPosition >= 2 && (pos - lastDotPosition) <= SNIPPET_LENGTH) {
			return lastDotPosition;
		} else {
			return pos;
		}
	}

	private Map<String, Integer> boldText(Map<String, Integer> source, List<String> words) {
		Map<String, Integer> resultMap = new HashMap<>();
		if (source == null)
			return resultMap;
		for (String key : source.keySet()) {
			int count = 0;
			for (String word : words) {
				if (key.contains(word)) {
					key = key.replaceAll(word, "<b>" + word + "</b>");
					count++;
				}
			}
			resultMap.put(key, count);
		}

		//Natural order
//		return resultMap.entrySet()
//				.stream()
//				.sorted(Map.Entry.comparingByValue())
//				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
//						(oldValue, newValue) -> oldValue, LinkedHashMap::new));

		//Reverse order
		return resultMap.entrySet()
				.stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(oldValue, newValue) -> oldValue, LinkedHashMap::new));
	}
}

