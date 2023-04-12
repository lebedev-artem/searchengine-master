package searchengine.tools;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import searchengine.tools.LemmaFinder;

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
	private final Integer SNIPPET_LENGTH = 150;
	private final Integer MAX_FULL_SNIPPET_LENGTH = 550;

	public void setText(String text) {
		this.text = Jsoup
				.clean(text, Safelist.simpleText())
				.replaceAll("[^А-Яа-яЁё\\d\\s,.!]+", "");
	}

	public void setQueryWords(List<String> queryWords) {
		this.queryWords = queryWords;
	}

	public Map<Integer, String> getWords(@NotNull String text) {
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

	public Map<Integer, Set<String>> getLemmas() {
		Map<Integer, String> words = getWords(text);
		Map<Integer, Set<String>> lemmas = new HashMap<>();
		for (Map.Entry<Integer, String> entry : words.entrySet()) {
			Set<String> lemmaSet = lemmaFinder.getLemmaSet(entry.getValue());
			if (!lemmaSet.isEmpty())
				lemmas.put(entry.getKey(), lemmaSet);
		}
		return lemmas;
	}

	public Map<Integer, String> getDirtyForms() {
		Map<Integer, Set<String>> lemmas = getLemmas();
		Map<Integer, String> dirtyForms = new HashMap<>();
		for (String queryWord : queryWords) {
			for (Map.Entry<Integer, Set<String>> entry : lemmas.entrySet()) {
				if (entry.getValue().contains(queryWord.toLowerCase())) {
					String word = getWords(text).get(entry.getKey());
					dirtyForms.put(entry.getKey(), word);
				}
			}
		}
		return dirtyForms;
	}

	public String generateSnippets() {
		String prevSnippet = "";
		Map<Integer, String> dirtyForms = getDirtyForms();
		List<Integer> sortedPositions = new ArrayList<>(dirtyForms.keySet());
		Collections.sort(sortedPositions);
		Map<String, Integer> markedSnipped = new HashMap<>();
		int totalLength = 0;
		int prevPos = -1;
		int start = -1;
		int end = -1;

		for (Integer pos : sortedPositions) {
			if (prevPos == -1) {
				start = getLastDotPositionInText(pos);
			} else {
				int gap = pos - prevPos - dirtyForms.get(prevPos).length();
				if (gap > SNIPPET_LENGTH) {
					start = getLastDotPositionInText(pos);
					markedSnipped.put(prevSnippet, 0);
				}
			}
			end = Math.min(pos + dirtyForms.get(pos).length() + SNIPPET_LENGTH / 2, text.length());
			prevSnippet = text.substring(start, end);
			totalLength = totalLength + prevSnippet.length();

			if (totalLength >= MAX_FULL_SNIPPET_LENGTH) break;
			prevPos = pos;
		}

		if (!prevSnippet.isEmpty()) markedSnipped.put(prevSnippet, 0);

		List<String> list = new ArrayList<>(dirtyForms.values());
		Map<String, Integer> resultBoldedList = boldText(markedSnipped, list);
		StringBuilder sb = getResultSnippet(resultBoldedList);

		return sb.toString();
	}

	private @NotNull StringBuilder getResultSnippet(@NotNull Map<String, Integer> resultBoldedList) {
		StringBuilder sb = new StringBuilder();
		for (String s : resultBoldedList.keySet()) {
			sb.append("&#8195").append(s).append(" . . .").append("<br><br>");
			if (sb.length() >= MAX_FULL_SNIPPET_LENGTH)
				break;
		}
		return sb;
	}

	private int getLastDotPositionInText(Integer pos) {
		int lastDotPosition = text.substring(0, pos).lastIndexOf(".") + 2;
		if (lastDotPosition >= 2) return lastDotPosition;
		else return pos;

	}

	private Map<String, Integer> boldText(@NotNull Map<String, Integer> source, List<String> words) {
		Map<String, Integer> resultMap = new HashMap<>();
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
		return resultMap.entrySet()
				.stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(oldValue, newValue) -> oldValue, LinkedHashMap::new));
	}
}

