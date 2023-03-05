package searchengine.services.queues;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.PageEntity;
import searchengine.repositories.PageRepository;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
@Getter
@Setter
@Component
public class PagesSavingService {
	private BlockingQueue<PageEntity> queue;
	private Future<?> futureForScrapingSite;
	@Autowired
	PageRepository pageRepository;


	public void pagesSaving(@NotNull BlockingQueue<PageEntity> queueOfPagesForSaving, Future<?> futureForScrapingSite) {
		while (true) {
			try {
				PageEntity pageEntity = queueOfPagesForSaving.take();
				if (!pageRepository.existsById(pageEntity.getId()))
					pageRepository.save(pageEntity);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (futureForScrapingSite.isDone() && !queueOfPagesForSaving.iterator().hasNext())
				return;
		}
	}
}
