package searchengine.services.utilities;

import net.bytebuddy.asm.Advice;
import org.hibernate.type.LocalDateTimeType;
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;

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