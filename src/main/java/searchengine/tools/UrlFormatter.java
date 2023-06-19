package searchengine.tools;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import searchengine.config.Site;
import searchengine.config.SitesList;

@Setter
public class UrlFormatter {
	private SitesList sitesList;

	public static int findNthOccurrence(@NotNull String str, char ch, int n) {
		int index = str.indexOf(ch);
		while (--n > 0 && index != -1) {
			index = str.indexOf(ch, index + 1);
		}
		return index;
	}

	public static @NotNull String getShortUrl(@NotNull String url) {
		String result = url.substring(0, findNthOccurrence(url, '/', 3) + 1);
		if (!result.equals("")){
			return result;
		}
		return url;
	}

	public String getHomeSiteUrl(String url){
		String result = null;
		for (Site s: sitesList.getSites()) {
			if (s.getUrl().startsWith(UrlFormatter.getShortUrl(url))){
				result = s.getUrl();
				break;
			}
		}
		return result;
	}

}
