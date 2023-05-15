package searchengine.tools;

import java.util.ArrayList;
import java.util.List;

public class Regex {
	public static final String URL_IS_FILE_LINK = "https?:/(?:/[^/]+)+/[А-Яа-яёЁ\\w\\W ]+\\.[\\wa-z]{2,5}(?!/|[\\wА-Яа-яёЁ])";
	public static final String URL_IS_VALID = "^(ht|f)tp(s?)://[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(/?)([a-zA-Z0-9\\-.,'=/\\\\+%_]*)?$";

	public static final List<String> HTML_EXT = new ArrayList<>() {{
		add("html");
		add("dhtml");
		add("shtml");
		add("xhtml");
	}};
}
