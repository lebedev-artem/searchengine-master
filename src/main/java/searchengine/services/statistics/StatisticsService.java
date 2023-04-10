package searchengine.services.statistics;

import lombok.extern.slf4j.Slf4j;
import searchengine.dto.statistics.StatisticsResponse;

public interface StatisticsService {

    StatisticsResponse getStatistics();
}
