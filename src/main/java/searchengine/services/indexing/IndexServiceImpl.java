package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;
import searchengine.services.utilities.ParsingEngine;
import searchengine.services.utilities.URLNameFormatter;

import java.time.LocalDateTime;

@Transactional
@Component
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService{

	@Autowired
	private SiteRepository siteRepository;
//	private static Logger logger;
	private static final Marker INFO = MarkerManager.getMarker("INFO");
	private static final Marker INPUT_ERR = MarkerManager.getMarker("INPUT_ERR");
	private static final Marker EXCEPTIONS = MarkerManager.getMarker("EXCEPTIONS");

	@Override
	public void indexingStart(Site site) {
		SiteEntity siteEntity = new SiteEntity();
		siteEntity.setUrl(site.getUrl());
		siteEntity.setName(site.getName());
		siteEntity.setStatus(Status.INDEXING.toString());
		siteEntity.setLastError("last error");
		siteEntity.setStatusTime(LocalDateTime.now());
		siteRepository.save(siteEntity);
	}

	@Override
	public void indexingStop() {
		System.out.printf("stop");
	}
}
