package searchengine.model;

public enum  IndexingStatus
{
	INDEXING("INDEXING"),
	INDEXED("INDEXED"),
	FAILED("FAILED");

	public final String status;

	IndexingStatus(String status){
		this.status = status;
	}

	public String toString() {
		return this.status;
	}
}
