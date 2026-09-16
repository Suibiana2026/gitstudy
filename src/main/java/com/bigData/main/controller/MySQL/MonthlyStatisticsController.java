package com.bigData.main.controller.MySQL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bigData.main.pojo.MonthlyStatistics;
import com.bigData.main.service.MySQL.MonthlyStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MonthlyStatisticsController {
    private static final Logger logger = LoggerFactory.getLogger(MonthlyStatisticsController.class);

    @Autowired
    private MonthlyStatisticsService service;

    @GetMapping("/monthly_statistics")
    public List<MonthlyStatistics> getMonthlyStatistics() {
        logger.info("Received request to fetch monthly statistics");

        // 调用服务层获取数据
        List<MonthlyStatistics> statistics = service.getAllStatistics();

        // 验证数据并打印发送的数据
        if (statistics == null) {
            logger.error("Service returned null statistics data");
        } else if (statistics.isEmpty()) {
            logger.warn("No monthly statistics data found");
        } else {
            logger.info("Sending {} monthly statistics records to frontend", statistics.size());
            // 打印详细数据（仅在 DEBUG 级别）
            logStatisticsData(statistics);
        }

        return statistics;
    }

    /**
     * 打印统计数据的详细内容
     * @param statistics 统计数据列表
     */
    private void logStatisticsData(List<MonthlyStatistics> statistics) {
        if (logger.isDebugEnabled()) {
            logger.debug("Detailed monthly statistics data to be sent:");
            int index = 1;
            // 限制打印的数据量，避免日志过大
            int maxRecordsToLog = Math.min(statistics.size(), 5); // 最多打印 5 条
            for (int i = 0; i < maxRecordsToLog; i++) {
                logger.debug("Record {}: {}", index++, statistics.get(i));
            }
            if (statistics.size() > maxRecordsToLog) {
                logger.debug("... {} more records not shown", statistics.size() - maxRecordsToLog);
            }
        } else {
            logger.info("Detailed data logging skipped (DEBUG level not enabled)");
        }
    }
}