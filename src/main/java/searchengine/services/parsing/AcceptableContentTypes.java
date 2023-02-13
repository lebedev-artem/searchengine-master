package searchengine.services.parsing;

import java.util.Arrays;

public class AcceptableContentTypes {
	public final String[] acceptableContentTypes = new String[]{
			"text/plain",
			"text/html",
			"application/xhtml+xml",
			"application/xml",
			"application/vnd.oasis.opendocument.text",
			"application/rtf"};

	public boolean contains(String contentType){
		for (String type : acceptableContentTypes) if (contentType.contains(type)) return true;
		return false;
	}
}
