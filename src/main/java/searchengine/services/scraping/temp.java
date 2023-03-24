//import java.io.IOException;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.concurrent.ForkJoinPool;
//import java.util.concurrent.RecursiveAction;
//
//import org.jsoup.Connection.Response;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;
//
//public class SiteCrawler {
//
//	private static final int MAX_DEPTH = 5; // максимальная глубина поиска
//	private static final int MAX_THREADS = 10; // максимальное количество потоков
//	private static final String[] START_URLS = {"https://www.example.com"}; // стартовые URL
//	private static final Set<String> visitedUrls = new HashSet<>(); // посещенные URL
//	private static final ForkJoinPool pool = new ForkJoinPool(MAX_THREADS); // пул потоков
//
//	public static void main(String[] args) {
//		for (String url : START_URLS) {
//			pool.invoke(new SiteCrawlAction(url, 0));
//		}
//		pool.shutdown();
//	}
//
//	private static class SiteCrawlAction extends RecursiveAction {
//
//		private final String url;
//		private final int depth;
//
//		public SiteCrawlAction(String url, int depth) {
//			this.url = url;
//			this.depth = depth;
//		}
//
//		@Override
//		protected void compute() {
//			if (depth >= MAX_DEPTH || visitedUrls.contains(url)) {
//				return;
//			}
//			visitedUrls.add(url);
//			try {
//				Response response = Jsoup.connect(url).execute();
//				int statusCode = response.statusCode();
//				if (statusCode == 200) {
//					Document doc = response.parse();
//					Elements links = doc.select("a[href]");
//					for (Element link : links) {
//						String linkUrl = link.absUrl("href");
//						if (!visitedUrls.contains(linkUrl)) {
//							SiteCrawlAction action = new SiteCrawlAction(linkUrl, depth + 1);
//							pool.execute(action);
//						}
//					}
//				}
//			} catch (IOException e) {
//				System.out.println("Error connecting to " + url + ": " + e.getMessage());
//			}
//		}
//	}
//}