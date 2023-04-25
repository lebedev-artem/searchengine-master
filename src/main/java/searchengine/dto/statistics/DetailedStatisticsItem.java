package searchengine.dto.statistics;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

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

    public void setStatusTime(@NotNull LocalDateTime statusTime) {
        this.statusTime = Date.from(statusTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}

