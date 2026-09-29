package com.sparepart.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.sql.Timestamp;
import org.springframework.transaction.annotation.Transactional;

/**
 * 冷热分离定时任务：每月1日凌晨2点，将 6 个月前的 operation_logs 数据迁移至归档表。
 */
@Slf4j
@Component
public class ArchiveTask {

    private final JdbcTemplate jdbcTemplate;
    private final int hotMonths;

    public ArchiveTask(JdbcTemplate jdbcTemplate,
                       @Value("${app.log-hot-months:6}") int hotMonths) {
        this.jdbcTemplate = jdbcTemplate;
        this.hotMonths = hotMonths;
    }

    @Scheduled(cron = "0 0 2 1 * ?")
    @Transactional
    public void archive() {
        LocalDate cutoff = LocalDate.now().minusMonths(hotMonths).withDayOfMonth(1);
        Timestamp earliest = jdbcTemplate.query(
                "SELECT MIN(created_at) FROM operation_logs WHERE created_at < ?",
                ps -> ps.setTimestamp(1, Timestamp.valueOf(cutoff.atStartOfDay())),
                rs -> rs.next() ? rs.getTimestamp(1) : null);
        if (earliest == null) return;
        YearMonth month = YearMonth.from(earliest.toLocalDateTime());
        YearMonth endMonth = YearMonth.from(cutoff);
        int totalMoved = 0;
        while (month.isBefore(endMonth)) {
            String table = "operation_logs_" + month.format(DateTimeFormatter.ofPattern("yyyy_MM"));
            LocalDate start = month.atDay(1);
            LocalDate end = month.plusMonths(1).atDay(1);
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table + " LIKE operation_logs");
            int moved = jdbcTemplate.update("INSERT IGNORE INTO " + table + " SELECT * FROM operation_logs " +
                            "WHERE created_at >= ? AND created_at < ?",
                    Timestamp.valueOf(start.atStartOfDay()), Timestamp.valueOf(end.atStartOfDay()));
            if (moved > 0) {
                jdbcTemplate.update("DELETE FROM operation_logs WHERE created_at >= ? AND created_at < ?",
                        Timestamp.valueOf(start.atStartOfDay()), Timestamp.valueOf(end.atStartOfDay()));
                totalMoved += moved;
            }
            month = month.plusMonths(1);
        }
        log.info("冷数据按实际月份归档完成，共迁移 {} 条", totalMoved);
    }
}
