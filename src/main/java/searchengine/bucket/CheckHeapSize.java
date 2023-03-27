//package searchengine.services.stuff;
//
//public class CheckHeapSize {
//	public static void main(String[] args) {
//		long heapSize = Runtime.getRuntime().totalMemory();
//
//		// Get maximum size of heap in bytes. The heap cannot grow beyond this size.// Any attempt will result in an OutOfMemoryException.
//		long heapMaxSize = Runtime.getRuntime().maxMemory();
//
//		// Get amount of free memory within the heap in bytes. This size will increase // after garbage collection and decrease as new objects are created.
//		long heapFreeSize = Runtime.getRuntime().freeMemory();
//
//		System.out.println("heap size: " + formatSize(heapSize));
//		System.out.println("heap max size: " + formatSize(heapMaxSize));
//		System.out.println("heap free size: " + formatSize(heapFreeSize));
//
//	}
//	public static String formatSize(long v) {
//		if (v < 1024) return v + " B";
//		int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
//		return String.format("%.1f %sB", (double)v / (1L << (z*10)), " KMGTPE".charAt(z));
//	}
//}
