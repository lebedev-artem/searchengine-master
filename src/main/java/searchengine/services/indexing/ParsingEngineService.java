package searchengine.services.indexing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.context.properties.ConfigurationProperties;
import searchengine.services.utilities.URLNameFormatter;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

public class ParsingEngineService {
	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
	String regexLinkIsFile = "http[s]?:/(?:/[^/]+){1,}/[А-Яа-яёЁ\\w ]+\\.[a-z]{3,5}(?![/]|[\\wА-Яа-яёЁ])";
	String regexValidURL = "^(ht|f)tp(s?)://[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(/?)([a-zA-Z0-9\\-.?,'/\\\\+&%_]*)?$";
	URLNameFormatter URLFormatter = new URLNameFormatter();
	private String zeroLevelURL;
	private static final Logger logger = LogManager.getLogger(ParsingEngineService.class);

	public ParsingEngineService(){
	}

	public boolean linkIsFile(String link){
		return link.matches(regexLinkIsFile) ? true : false;
	}

	public Map<String, Integer> getChildLinksFromElements(String url) throws InterruptedException, IOException {
		Date date = new Date();
		Map<String, Integer> t_links = new TreeMap<>();
		try {
			Elements t_elements = getElementsFromURL(url);
			if (t_elements != null && t_elements.size() != 0) {
				for (Element element : t_elements) {
//					String outputPathFile = "./src/main/resources/content_" + element.baseUri() + "_.txt";
//					String content = element.html();
					String s = URLFormatter.extractLink(element);
//					PrintWriter writer = new PrintWriter(outputPathFile);
//					writer.println(content);
//					writer.flush();
//					writer.close();
					String cleanS = URLFormatter.cleanURLName(s);
					if (s.matches(regexValidURL) //Проверяем валидность сслыки
							&& s.contains(URLFormatter.cleanURLName(zeroLevelURL)) //Ссылка того же домена как и домен вызвавшей
							&& !cleanS.equals(URLFormatter.cleanURLName(url)) //Ссылка не равна вызвавшей ссылке, loop
							&& cleanS.indexOf(URLFormatter.cleanURLName(url)) == 0
							//Ссылка того же уровня как вызвавшая, тоже устраняет loop
							//как проверять итоговую коллекцию на предмет наличия ссылки еще не додумал
							//под ТЗ подходит. Ссылки не уходят на уровень вниз, т.е. строят дерево вперед, и за стартовую страницу
							//берут тот уровень ссылки, которая на входе
							&& !t_links.containsKey(s)) //Ссылка еще не добавлена во временную Map
					{
						if (s.matches(regexLinkIsFile)) {
							t_links.put(s, URLFormatter.getLevel(cleanS) - 1);
//                            System.out.println("Added " + s);
						} else {
							t_links.put(s, URLFormatter.getLevel(cleanS));
//                            System.out.println("Added " + s);
						}
					}
				}
			}
//			else {
//				System.out.println(formatter.format(date) + " Elements object is null from " + url);
//			}
		} catch (NullPointerException ex) {
//            ex.printStackTrace();
			System.out.println(formatter.format(date) + " Error in <getChildLinksFromElements>. Elements from URL is empty\n");
		}
//		if (t_links.size() > 0) {
//			System.out.println(formatter.format(date) + " " + Thread.currentThread().getName() + ", parsed " + t_links.size() + " links <- " + url);
//		}
		return t_links;
	}

	@ConfigurationProperties(prefix = "jsoup-setting")
	private Elements getElementsFromURL(String url){
		Date date = new Date();
		Elements elements = new Elements();
//		Connection.Response conResponse;
//		try {
//			conResponse = Jsoup.connect(url).execute();
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}
//		System.out.println(conResponse);

		try {
			Document doc = Jsoup.connect(url)
					.get();
			elements = doc.select("a[href]");
		} catch (UnknownHostException ex) {
//            ex.printStackTrace();
			System.out.println(formatter.format(date) + " Exception in <getElementsFromURL>. " + url + " not available");
		} catch (SocketTimeoutException ex) {
//            ex.printStackTrace();
			logger.error("Connect timed out / Read timed out");
//			System.out.println(formatter.format(date) + " Connect timed out / Read timed out");
		} catch (UnsupportedMimeTypeException ex) {
//            ex.printStackTrace();
			System.out.println(formatter.format(date) + " Unhandled content type. Must be text/*, application/xml, or application/*+xml. Mimetype=application/json, URL= " + url);
		} catch (IOException ex) {
			System.out.println(formatter.format(date) + " Too many redirects occurred trying to load URL " + url);
		}
		return elements;
	}

	public String getZeroLevelURL() {
		return zeroLevelURL;
	}

	public void setZeroLevelURL(String zeroLevelURL) {
		this.zeroLevelURL = zeroLevelURL;
	}
}
