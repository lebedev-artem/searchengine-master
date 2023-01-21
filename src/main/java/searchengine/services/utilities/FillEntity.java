package searchengine.services.utilities;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import java.time.LocalDateTime;

@Service
public class FillEntity implements FillOutEntity{

	public SiteEntity fillSiteEntity(Enum<Status> status, String lastError, String url, String name){
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setStatus(String.valueOf(status));
		siteEntity.setStatusTime(LocalDateTime.now());
		siteEntity.setLastError(lastError);
		siteEntity.setUrl(url);
		siteEntity.setName(name);
		return siteEntity;
	}
}