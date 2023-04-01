package searchengine.services.stuff;

import org.springframework.stereotype.Component;

@Component
public class CheckHeapSize {

	long heapSize;
	long heapMaxSize;
	long heapFreeSize;

	public String formatSize(long v) {
		heapSize = Runtime.getRuntime().totalMemory();
		heapMaxSize = Runtime.getRuntime().maxMemory();
		heapFreeSize = Runtime.getRuntime().freeMemory();
		if (v < 1024) return v + " B";
		int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
		return String.format("%.1f %sB", (double)v / (1L << (z*10)), " KMGTPE".charAt(z));
	}

	public String getHeap(){
		return formatSize(heapSize);
	}

	public String getFreeHeap(){
		return formatSize(heapFreeSize);
	}
}
