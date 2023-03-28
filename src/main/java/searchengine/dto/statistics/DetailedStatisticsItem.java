package searchengine.dto.statistics;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private Date statusTime;
    private String error;
    private int pages;
    private int lemmas;

    public void setStatusTime(LocalDateTime statusTime) {
        this.statusTime = java.util.Date
                .from(statusTime
                        .atZone(ZoneId.systemDefault())
                        .toInstant());
    }
}

