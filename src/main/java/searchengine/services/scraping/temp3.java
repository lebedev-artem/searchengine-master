//import java.io.IOException;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.concurrent.ConcurrentLinkedDeque;
//import java.util.concurrent.ForkJoinPool;
//import java.util.concurrent.RecursiveAction;
//
//public class LinkParser extends RecursiveAction {
//	private final String url;
//	private final Set<String> visited;
//	private final ConcurrentLinkedDeque<String> queue;
//
//	public LinkParser(String url, Set<String> visited, ConcurrentLinkedDeque<String> queue) {
//		this.url = url;
//		this.visited = visited;
//		this.queue = queue;
//	}
//
//	@Override
//	protected void compute() {
//		try {
//			URL urlObj = new URL(url);
//			HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
//			connection.setRequestMethod("HEAD");
//
//			if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
//				System.out.println(url);
//
//				synchronized (visited) {
//					visited.add(url);
//				}
//
//				connection = (HttpURLConnection) urlObj.openConnection();
//				connection.setRequestMethod("GET");
//
//				String content = connection.getContent().toString();
//				// extract links from content and add to queue
//				// ...
//
//				for (String link : queue) {
//					if (!visited.contains(link)) {
//						queue.add(link);
//						LinkParser task = new LinkParser(link, visited, queue);
//						task.fork();
//					}
//				}
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}
//
//	public static void main(String[] args) {
//		String startUrl = "https://example.com/";
//		Set<String> visited = new HashSet<>();
//		ConcurrentLinkedDeque<String> queue = new ConcurrentLinkedDeque<>();
//		queue.add(startUrl);
//
//		ForkJoinPool pool = new ForkJoinPool();
//
//		while (!queue.isEmpty()) {
//			String url = queue.poll();
//			if (!visited.contains(url)) {
//				visited.add(url);
//				LinkParser task = new LinkParser(url, visited, queue);
//				pool.execute(task);
//			}
//		}
//
//		pool.shutdown();
//	}
//}