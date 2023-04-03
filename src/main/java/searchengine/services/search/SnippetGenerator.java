package searchengine.services.search;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import searchengine.services.lemmatization.LemmaFinder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Getter
@Setter
@RequiredArgsConstructor
public class SnippetGenerator {
	//	private final LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
	private final LemmaFinder lemmaFinder;
	private final Integer SNIPPET_LENGTH = 100;
	private final Integer MAX_FULL_SNIPPET_LENGTH = 550;

	public void setText(String text) {
		this.text = Jsoup.clean(text, Safelist.simpleText()).replaceAll("[^А-Яа-яЁё\\d\\s]+", "");
	}

	private String text;

	public void setQueryWords(List<String> queryWords) {
		this.queryWords = queryWords;
	}

	private List<String> queryWords;


	public Map<Integer, String> getWords(String text) {
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
		StringBuilder sb = new StringBuilder();
		Map<Integer, String> dirtyForms = getDirtyForms();
		List<Integer> sortedPositions = new ArrayList<>(dirtyForms.keySet());
		Collections.sort(sortedPositions);
		int prevPos = -1;
		int start = -1;
		int end = -1;

		for (Integer pos : sortedPositions) {
			if (prevPos == -1) { // первый сниппет
				Pattern pattern = Pattern.compile("[.?!][^\\d\\W]|$");
				Matcher matcher = pattern.matcher(text.substring(0, pos));
				int sentenceStart = 0;
				if (matcher.find()) {
					sentenceStart = matcher.end();
				}
				start = Math.max(pos - SNIPPET_LENGTH / 2, 0);
				end = Math.min(pos + dirtyForms.get(pos).length() + SNIPPET_LENGTH / 2, text.length());
				prevSnippet = text.substring(sentenceStart, end); //запоминаем строку
			} else {
				int gap = pos - prevPos - dirtyForms.get(prevPos).length();
				if (gap <= SNIPPET_LENGTH) { // расширяем пред строку
					end = Math.min(pos + dirtyForms.get(pos).length() + SNIPPET_LENGTH / 2, text.length());
					String snippet = text.substring(start, end); //создаем новую строку со старым старт и новым энд
					prevSnippet = snippet; //запоминаем ее
				} else { // новый сниппет
					Pattern pattern = Pattern.compile("[.?!][^\\d\\W]|$");
					Matcher matcher = pattern.matcher(text.substring(0, pos));
					int sentenceStart = 0;
					if (matcher.find()) {
						sentenceStart = matcher.end();
					}
					sb.append("&#8195");
					sb.append(prevSnippet); //жлюавляем в СБ старую строку. Потом ...
					sb.append("&#8195 . . .").append("<br><br>");
					start = Math.max(pos - SNIPPET_LENGTH / 2, 0);
					end = Math.min(pos + dirtyForms.get(pos).length() + SNIPPET_LENGTH / 2, text.length());
					String snippet = text.substring(sentenceStart, end);
					sb.append("&#8195");
					sb.append(snippet); //добавляем новую строку
					prevSnippet = "";
				}
			}
			if (sb.length() >= MAX_FULL_SNIPPET_LENGTH)
				break;
			prevPos = pos;
		}
		if (!prevSnippet.isEmpty())
			sb.append(prevSnippet);
		sb.append("...");
		Collection<String> collection = dirtyForms.values();
		List<String> list = new ArrayList<>(collection);
		sb = boldText(sb, list);


		return sb.toString();
	}

	private StringBuilder boldText(StringBuilder input, List<String> words) {
		StringBuilder sb = new StringBuilder(input);

		String regex = "\\b(?:" + String.join("|", words) + ")\\b";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(sb);

		while (matcher.find()) {
			int startIndex = matcher.start();
			int endIndex = matcher.end();
			sb.insert(endIndex, "</b>");
			sb.insert(startIndex, "<b>");
		}
		return sb;
	}
}
