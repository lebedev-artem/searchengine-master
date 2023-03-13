package searchengine.model;

public enum StatusIndexing
{
	INDEXING("INDEXING"),
	INDEXED("INDEXED"),
	FAILED("FAILED");

	public final String status;

	StatusIndexing (String status){
		this.status = status;
	}

	public String toString() {
		return this.status;
	}
}
