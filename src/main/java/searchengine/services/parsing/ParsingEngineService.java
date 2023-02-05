//package searchengine.services.parsing;
//
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.hibernate.transform.PassThroughResultTransformer;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//import org.jsoup.Connection;
//import org.jsoup.Jsoup;
//import org.jsoup.UnsupportedMimeTypeException;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.context.properties.ConfigurationProperties;
//import searchengine.services.indexing.IndexServiceImpl;
//import searchengine.services.interfaces.IndexService;
//import searchengine.services.utilities.UrlFormatter;
//
//import java.io.IOException;
//import java.net.SocketTimeoutException;
//import java.net.UnknownHostException;
//import java.text.SimpleDateFormat;
//import java.util.*;
//
//public class ParsingEngineService {
//	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
//	String regexLinkIsFile = "http[s]?:/(?:/[^/]+){1,}/[А-Яа-яёЁ\\w ]+\\.[a-z]{3,5}(?![/]|[\\wА-Яа-яёЁ])";
//	String regexValidURL = "^(ht|f)tp(s?)://[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(/?)([a-zA-Z0-9\\-.?,'/\\\\+&%_]*)?$";
//	UrlFormatter urlFormatter = new UrlFormatter();
//	private String zeroLevelUrl;
//	private static final Logger logger = LogManager.getLogger(ParsingEngineService.class);
//	private final AcceptableContentTypes acceptableContentTypes = new AcceptableContentTypes();
//	Integer statusCode;
//	private Connection.Response connectionResponse;
//	@Autowired
//	private IndexServiceImpl indexService;
//
//
//	public ParsingEngineService(){
//	}
//
//	public boolean linkIsFile(@NotNull String link){
//		return link.matches(regexLinkIsFile);
//	}
//
//	public Map<String, Integer> getSubLinks(String url) throws InterruptedException, IOException {
//		Map<String, Integer> subLinks = new HashMap<>();
//		Elements elements = new Elements();
//		Document document;
//		connectionResponse = getResponseFromUrl(url);
//		indexService.getMainLinks().put("aaa", 1);
//		try {
//			elements = connectionResponse.parse().select("a[href]");
//			statusCode = connectionResponse.statusCode();
//			if (elements.size() == 0) return subLinks;
//
//			for (Element element : elements) {
//					String s = urlFormatter.extractLink(element);
//					String cleanS = urlFormatter.cleanUrl(s);
//
//					if (s.matches(regexValidURL) //Проверяем валидность сслыки
//							&& s.contains(urlFormatter.cleanUrl(zeroLevelUrl)) //Ссылка того же домена как и домен вызвавшей
//							&& !cleanS.equals(urlFormatter.cleanUrl(url)) //Ссылка не равна вызвавшей ссылке, loop
//							&& cleanS.indexOf(urlFormatter.cleanUrl(url)) == 0
//							//Ссылка того же уровня как вызвавшая, тоже устраняет loop
//							//как проверять итоговую коллекцию на предмет наличия ссылки еще не додумал
//							//под ТЗ подходит. Ссылки не уходят на уровень вниз, т.е. строят дерево вперед, и за стартовую страницу
//							//берут тот уровень ссылки, которая на входе
//							&& !subLinks.containsKey(s)) //Ссылка еще не добавлена во временную Map
//					{
//						if (s.matches(regexLinkIsFile)) {
//							subLinks.put(s, statusCode);
////                            System.out.println("Added " + s);
//						} else {
//							subLinks.put(s, statusCode);
////                            System.out.println("Added " + s);
//						}
//					}
//				}
//
////			else {
////				System.out.println(formatter.format(date) + " Elements object is null from " + url);
////			}
//		} catch (NullPointerException ex) {
////            ex.printStackTrace();
//			System.out.println(formatter.format(new Date()) + " Error in <getChildLinksFromElements>. Elements from URL is empty\n");
//		}
////		if (subLinks.size() > 0) {
////			System.out.println(formatter.format(date) + " " + Thread.currentThread().getName() + ", parsed " + subLinks.size() + " links <- " + url);
////		}
//		return subLinks;
//	}
//
//	@ConfigurationProperties(prefix = "jsoup-setting")
//	private @Nullable Connection.Response getResponseFromUrl(String url) throws IOException, UnknownHostException, SocketTimeoutException, UnsupportedMimeTypeException {
//		connectionResponse = Jsoup.connect(url).execute();
//
//		if (!acceptableContentTypes.contains(connectionResponse.contentType())){
//			logger.error("connectionResponse = Jsoup.connect(url).execute(). Not acceptable content type");
//			logger.warn(connectionResponse.url());
//			return null;
//		}
//		return connectionResponse;
//	}
//
//	public void setZeroLevelUrl(String zeroLevelUrl) {
//		this.zeroLevelUrl = zeroLevelUrl;
//	}
//}
