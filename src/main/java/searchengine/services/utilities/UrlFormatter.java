package searchengine.services.utilities;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;

public class UrlFormatter {
	String regexProtocol = "^(http|https)://(www.)?";

	public Integer getLevel(String url) {
		return StringUtils.countMatches(url, "/");
	}

	public String cleanHref(String str) {
		str = str.replaceAll(regexProtocol, "");
		if (str.lastIndexOf("/") == str.length() - 1) str = str.substring(str.indexOf(str), str.length() - 1);
		return str;
	}

	public String getHref(Element element) {
		if (element != null) {
			return element.absUrl("href");
		}
		return "http://google.com";
//        Во время тестирования приложения, случались эксепшены с нулевой строкой, с которой я хочу сделать или
//        substring или equal. Пусть ллучше это будет такая строка, чем эксепнш, логику работы она не портит
	}

}
