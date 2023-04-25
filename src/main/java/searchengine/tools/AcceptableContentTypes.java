package searchengine.tools;

public class AcceptableContentTypes {
	public final String[] acceptableContentTypes = {
			"text/plain",
			"text/html",
			"application/xhtml+xml",
			"application/xml",
			"application/rtf"};

	public boolean contains(String contentType){
		for (String type : acceptableContentTypes)
			if (contentType.contains(type)) return true;
		return false;
	}
}
