package com.bigData.main.service.MySQL;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计结果入库服务
 * <p>
 * 把 HDFS 上的采集数据（CSV）用 Spark 做分组统计，结果写入 MySQL，
 * 让大屏（品牌销量排行）展示真实的采集数据，打通「采集 -> 处理 -> 展示」闭环。
 */
@Service
public class StatisticsImportService {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsImportService.class);

    private static final String TARGET_TABLE = "automobile_brand_sales_ranking";
    private static final String BACKUP_TABLE = "automobile_brand_sales_ranking_backup";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${hadoop.fs.defaultFS}")
    private String defaultFS;

    /**
     * 读取 HDFS 上的 CSV，按指定字段统计出现次数，取前 N 条写入 MySQL 大屏数据表
     *
     * @param filePath HDFS 文件路径（如 /original_data/xxx.csv）
     * @param field    统计字段名（CSV 表头中的列名；若不存在则回退到第二列）
     * @param topN     保留条数
     * @return 入库结果
     */
    public Map<String, Object> importStatisticsToDatabase(String filePath, String field, int topN) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        if (topN <= 0) {
            topN = 10;
        }

        String fullPath = filePath.startsWith("hdfs://") ? filePath : defaultFS + filePath;
        logger.info("开始统计入库，文件: {}, 字段: {}, topN: {}", fullPath, field, topN);

        SparkSession spark = SparkSession.builder()
                .appName("ImportStatisticsToDB")
                .master("local[*]")
                .config("spark.hadoop.fs.defaultFS", defaultFS)
                .getOrCreate();
        spark.sparkContext().setLogLevel("WARN");

        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Dataset<Row> df = spark.read()
                    .option("header", "true")
                    .option("inferSchema", "false")
                    .csv(fullPath);

            if (df.columns().length == 0) {
                throw new IllegalArgumentException("文件中没有可解析的列");
            }

            // 确定统计列：优先使用指定字段，否则回退到第二列
            List<String> columns = Arrays.asList(df.columns());
            String targetColumn = field;
            boolean fallback = false;
            if (targetColumn == null || targetColumn.trim().isEmpty() || !columns.contains(targetColumn)) {
                targetColumn = columns.size() > 1 ? columns.get(1) : columns.get(0);
                fallback = true;
            }

            Dataset<Row> counts = df.groupBy(targetColumn)
                    .count()
                    .orderBy(functions.desc("count"))
                    .limit(topN);

            List<Row> rows = counts.collectAsList();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Row row : rows) {
                Object key = row.get(0);
                long count = row.getLong(1);
                if (key == null || String.valueOf(key).trim().isEmpty()) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", String.valueOf(key).trim());
                item.put("value", count);
                items.add(item);
            }

            if (items.isEmpty()) {
                throw new IllegalArgumentException("统计结果为空，未写入数据库");
            }

            // 备份原数据（首次执行时创建备份表），再替换目标表数据
            backupAndReplace(items, targetColumn);

            result.put("status", "success");
            result.put("filePath", filePath);
            result.put("field", targetColumn);
            result.put("fallbackField", fallback);
            result.put("sourceRows", df.count());
            result.put("importCount", items.size());
            result.put("items", items);
            result.put("targetTable", TARGET_TABLE);
            result.put("backupTable", BACKUP_TABLE);
            result.put("message", "统计入库完成，共写入 " + items.size() + " 条数据，可在大屏查看");

            logger.info("统计入库完成，写入 {} 条", items.size());
            return result;

        } finally {
            spark.stop();
        }
    }

    /**
     * 备份原大屏数据并写入新的统计结果
     */
    private void backupAndReplace(List<Map<String, Object>> items, String sourceColumn) {
        try {
            Integer backupCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, BACKUP_TABLE);
            if (backupCount != null && backupCount == 0) {
                jdbcTemplate.execute("CREATE TABLE " + BACKUP_TABLE + " AS SELECT * FROM " + TARGET_TABLE);
                logger.info("已创建备份表 {} 并保存原始数据", BACKUP_TABLE);
            }
        } catch (Exception e) {
            logger.warn("备份原数据失败（不影响入库）: {}", e.getMessage());
        }

        jdbcTemplate.execute("DELETE FROM " + TARGET_TABLE);
        for (Map<String, Object> item : items) {
            jdbcTemplate.update("INSERT INTO " + TARGET_TABLE + " (brand_name, sales) VALUES (?, ?)",
                    String.valueOf(item.get("name")), ((Number) item.get("value")).intValue());
        }
        logger.info("已替换表 {} 数据，来源列: {}", TARGET_TABLE, sourceColumn);
    }

    /**
     * 将大屏数据恢复到备份版本
     */
    public Map<String, Object> restoreFromBackup() {
        Map<String, Object> result = new LinkedHashMap<>();
        Integer backupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, BACKUP_TABLE);
        if (backupCount == null || backupCount == 0) {
            result.put("status", "error");
            result.put("message", "没有找到备份表，无法恢复");
            return result;
        }
        jdbcTemplate.execute("DELETE FROM " + TARGET_TABLE);
        jdbcTemplate.execute("INSERT INTO " + TARGET_TABLE + " (brand_name, sales) SELECT brand_name, sales FROM " + BACKUP_TABLE);
        result.put("status", "success");
        result.put("message", "已从备份表恢复原始数据");
        return result;
    }
}
