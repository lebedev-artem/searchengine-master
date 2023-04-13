package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import searchengine.dto.statistics.StatisticsResponse;

public interface StatisticsService {

    StatisticsResponse getStatistics();
}
