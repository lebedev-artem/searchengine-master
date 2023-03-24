package searchengine.services.indexing;

public enum IndexingMode {
	FULL("FULL"),
	PARTIAL("PARTIAL");

	public final String mode;

	IndexingMode(String mode) {
		this.mode = mode;
	}

	public String toString() {
		return this.mode;
	}
}


