package searchengine.services.search;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SnippetGenerator {

	public static @NotNull List<String> generateSnippets(String input, List<String> searchWords) {
		Document doc = Jsoup.parse(input);
		String cleanHtml = doc.body().text();
		List<String> snippets = new ArrayList<>();
		int maxLength = 100;
		int overlap = 10;

		for (String searchWord : searchWords) {
			int index = cleanHtml.indexOf(searchWord);
			if (index != -1) {
				int startIndex = Math.max(0, index - maxLength / 2);
				int endIndex = Math.min(cleanHtml.length(), index + maxLength / 2 + overlap);
				while (startIndex > 0 && !Character.isWhitespace(cleanHtml.charAt(startIndex - 1))) {
					startIndex--;
				}
				while (endIndex < cleanHtml.length() && !Character.isWhitespace(cleanHtml.charAt(endIndex))) {
					endIndex++;
				}
				String snippet = cleanHtml.substring(startIndex, endIndex).trim();
				if (startIndex > 0) {
					snippet = "..." + snippet;
				}
				if (endIndex < cleanHtml.length()) {
					snippet = snippet + "...";
				}
				snippet = snippet.replaceAll(searchWord, "<b>" + searchWord + "</b>");
				snippets.add(snippet);
			}
		}

		return snippets;
	}
}
