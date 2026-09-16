package com.bigData.main.controller.Spark;

import org.apache.hadoop.fs.*;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.bigData.main.service.MySQL.StatisticsImportService;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/Spark")
public class DataCleaningController {
    private static final Logger logger = LoggerFactory.getLogger(DataCleaningController.class);

    @Value("${hadoop.fs.defaultFS}")
    private String hdfsDefaultFS;
    @Value("${hadoop.hdfs.processed-data}")
    private String processedDataPath;

    @Autowired
    private StatisticsImportService statisticsImportService;

    /**
     * 统计入库：把 HDFS 上的采集数据按字段统计后写入 MySQL，供大屏展示
     * 请求参数：filePath（HDFS 路径）、field（统计字段）、topN（保留条数，默认 10）
     */
    @PostMapping(value = "/ImportToDatabase", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> importToDatabase(@RequestBody Map<String, Object> request) {
        try {
            String filePath = request.get("filePath") != null ? String.valueOf(request.get("filePath")) : "";
            String field = request.get("field") != null ? String.valueOf(request.get("field")) : "";
            int topN = 10;
            if (request.get("topN") != null) {
                try {
                    topN = Integer.parseInt(String.valueOf(request.get("topN")));
                } catch (NumberFormatException ignored) {
                }
            }
            return statisticsImportService.importStatisticsToDatabase(filePath, field, topN);
        } catch (Exception e) {
            logger.error("统计入库失败", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "error");
            error.put("message", "统计入库失败：" + e.getMessage());
            return error;
        }
    }

    /**
     * 恢复大屏原始数据
     */
    @PostMapping(value = "/RestoreDatabase")
    public Map<String, Object> restoreDatabase() {
        try {
            return statisticsImportService.restoreFromBackup();
        } catch (Exception e) {
            logger.error("恢复数据失败", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "error");
            error.put("message", "恢复失败：" + e.getMessage());
            return error;
        }
    }

    // 预览字段接口（保持不变）
    @PostMapping(value = "/PreviewFields", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<String> previewFields(@RequestBody Map<String, String> request) throws IOException {
        String filePath = request.get("filePath");
        SparkSession spark = SparkSession.builder()
                .appName("Field Preview")
                .master("local[*]")
                .config("spark.hadoop.fs.defaultFS", hdfsDefaultFS)
                .getOrCreate();

        org.apache.hadoop.conf.Configuration hadoopConf = spark.sparkContext().hadoopConfiguration();
        FileSystem fs = FileSystem.get(hadoopConf);

        String fileName = new Path(filePath).getName();
        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);

        List<String> fields = new ArrayList<>();
        if ("csv".equalsIgnoreCase(fileExtension)) {
            String encoding = detectEncoding(fs.open(new Path(filePath)));
            Dataset<Row> data = spark.read()
                    .option("header", "true")
                    .option("inferSchema", "true")
                    .option("encoding", encoding)
                    .csv(hdfsDefaultFS + filePath);
            fields = Arrays.asList(data.columns());
        } else if ("txt".equalsIgnoreCase(fileExtension)) {
            Dataset<Row> rawData = spark.read().text(hdfsDefaultFS + filePath);
            Row firstRow = rawData.first();
            String[] columns = firstRow.getString(0).split(",");
            for (int i = 0; i < columns.length; i++) {
                fields.add(String.valueOf(i + 1));
            }
        }

        spark.close();
        return fields;
    }
    // 修改后的去重接口
    @PostMapping(value = "/DuplicationEliminating", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> deduplicateFiles(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> filePaths = (List<String>) request.get("filePaths");
        String dedupFields = (String) request.get("dedupFields");

        if (filePaths == null || filePaths.isEmpty()) {
            logger.error("文件路径为空");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "文件路径不能为空");
            return errorResponse;
        }

        try {
            for (String filePath : filePaths) {
                String fileName = new Path(filePath).getName();
                String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);
                String newFileName = "deduplicated_" + fileName;
                String hdfsProcessedPath = (processedDataPath + "/" + newFileName)
                        .replace("\\", "/")
                        .replace("//", "/");

                logger.info("开始处理文件：{}，去重字段：{}，保存路径：{}", fileName, dedupFields, hdfsProcessedPath);
                performDeduplication(filePath, hdfsProcessedPath, dedupFields);
            }

            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", "文件去重处理完成");
            return successResponse;
        } catch (Exception e) {
            logger.error("去重失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "去重失败：" + e.getMessage());
            return errorResponse;
        }
    }
    // 修改后的词频统计接口
    @PostMapping(value = "/WordFrequencyStatistics", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> wordFrequencyStatistics(@RequestBody Map<String, Object> request) throws IOException {
        @SuppressWarnings("unchecked")
        List<String> filePaths = (List<String>) request.get("filePaths");
        String field = (String) request.get("field");

        if (filePaths == null || filePaths.isEmpty()) {
            logger.error("文件路径为空");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "文件路径不能为空");
            return errorResponse;
        }

        SparkSession spark = SparkSession.builder()
                .appName("Word Frequency Statistics")
                .master("local[*]")
                .config("spark.hadoop.fs.defaultFS", hdfsDefaultFS)
                .getOrCreate();

        try {
            org.apache.hadoop.conf.Configuration hadoopConf = spark.sparkContext().hadoopConfiguration();
            FileSystem fs = FileSystem.get(hadoopConf);

            String filePath = filePaths.get(0);
            String fileName = new Path(filePath).getName();
            String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);

            Dataset<Row> data = null;
            if ("csv".equalsIgnoreCase(fileExtension)) {
                String encoding = detectEncoding(fs.open(new Path(filePath)));
                data = spark.read()
                        .option("header", "true")
                        .option("inferSchema", "true")
                        .option("encoding", encoding)
                        .csv(hdfsDefaultFS + filePath);
            } else if ("txt".equalsIgnoreCase(fileExtension)) {
                Dataset<Row> rawData = spark.read().text(hdfsDefaultFS + filePath);
                String[] fieldIndices = field.split(",");
                Column[] columns = new Column[fieldIndices.length];
                for (int i = 0; i < fieldIndices.length; i++) {
                    int index = Integer.parseInt(fieldIndices[i].trim()) - 1;
                    columns[i] = functions.element_at(functions.split(rawData.col("value"), ","), index + 1).alias("field" + i);
                }
                data = rawData.select(columns);
            } else {
                throw new IllegalArgumentException("不支持的文件格式：" + fileExtension);
            }

            String[] selectedFields = field.split(",");
            Map<String, Map<String, Long>> wordFrequencyResult = new HashMap<>();

            for (int i = 0; i < selectedFields.length; i++) {
                String fieldName = "csv".equalsIgnoreCase(fileExtension) ? selectedFields[i] : "field" + i;
                Dataset<Row> selectedField = data.select(fieldName)
                        .na().drop()
                        .filter(data.col(fieldName).isNotNull());

                Dataset<Row> words = selectedField.select(
                                functions.explode(functions.split(selectedField.col(fieldName), "\\s+")).as("word"))
                        .groupBy("word")
                        .count();

                Map<String, Long> wordFreqMap = new HashMap<>();
                for (Row row : words.collectAsList()) {
                    String word = row.getString(0);
                    Long count = row.getLong(1);
                    wordFreqMap.put(word, count);
                }

                String originalFieldName = "csv".equalsIgnoreCase(fileExtension) ? selectedFields[i] : selectedFields[i];
                wordFrequencyResult.put(originalFieldName, wordFreqMap);
            }

            // 保存结果到HDFS
            String resultFileName = "word_freq_" + System.currentTimeMillis() + ".csv";
            String hdfsOutputPath = processedDataPath + "/" + resultFileName;
            Path outputPath = new Path(hdfsDefaultFS + hdfsOutputPath);

            StringBuilder csvContent = new StringBuilder("字段,词,频率\n");
            for (Map.Entry<String, Map<String, Long>> entry : wordFrequencyResult.entrySet()) {
                String fieldName = entry.getKey();
                for (Map.Entry<String, Long> wordEntry : entry.getValue().entrySet()) {
                    csvContent.append(fieldName).append(",")
                            .append(wordEntry.getKey()).append(",")
                            .append(wordEntry.getValue()).append("\n");
                }
            }

            try (FSDataOutputStream out = fs.create(outputPath)) {
                out.write(csvContent.toString().getBytes(StandardCharsets.UTF_8));
                logger.info("词频统计结果已保存到HDFS: {}", hdfsOutputPath);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "词频统计完成，结果已保存到HDFS");
            response.put("data", wordFrequencyResult);
            response.put("filePath", hdfsOutputPath);
            return response;
        } catch (Exception e) {
            logger.error("词频统计失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "词频统计失败：" + e.getMessage());
            return errorResponse;
        } finally {
            spark.close();
        }
    }
    // 修改后的去重逻辑
    private void performDeduplication(String hdfsInputPath, String hdfsOutputPath, String dedupFields) throws IOException {
        SparkSession spark = SparkSession.builder()
                .appName("Data Deduplication")
                .master("local[*]")
                .config("spark.hadoop.fs.defaultFS", hdfsDefaultFS)
                .config("spark.hadoop.fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
                .config("spark.hadoop.dfs.replication", "1")
                .getOrCreate();

        org.apache.hadoop.conf.Configuration hadoopConf = spark.sparkContext().hadoopConfiguration();
        FileSystem fs = FileSystem.get(hadoopConf);

        String fileName = new Path(hdfsInputPath).getName();
        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);

        if (fileExtension.isEmpty()) {
            throw new IllegalArgumentException("文件扩展名不能为空");
        }

        String fullHdfsInputPath = hdfsDefaultFS + hdfsInputPath;

        Dataset<Row> data = null;
        if ("csv".equalsIgnoreCase(fileExtension)) {
            String encoding = detectEncoding(fs.open(new Path(hdfsInputPath)));
            data = spark.read()
                    .option("header", "true")
                    .option("inferSchema", "true")
                    .option("encoding", encoding)
                    .csv(fullHdfsInputPath);
        } else if ("txt".equalsIgnoreCase(fileExtension)) {
            Dataset<Row> rawData = spark.read().text(fullHdfsInputPath);
            if ("_ALL_FIELDS_".equals(dedupFields)) {
                data = rawData.select(functions.split(rawData.col("value"), ",").alias("fields"))
                        .selectExpr("fields.*");
            } else {
                String[] fieldIndices = dedupFields.split(",");
                Column[] dedupColumns = new Column[fieldIndices.length];
                for (int i = 0; i < fieldIndices.length; i++) {
                    int columnIndex = Integer.parseInt(fieldIndices[i].trim()) - 1;
                    dedupColumns[i] = functions.element_at(functions.split(rawData.col("value"), ","), columnIndex + 1).alias("dedup_column_" + i);
                }
                data = rawData.select(dedupColumns).withColumn("value", rawData.col("value"));
            }
        } else {
            throw new IllegalArgumentException("不支持的文件格式：" + fileExtension);
        }

        Dataset<Row> deduplicatedData;
        if ("_ALL_FIELDS_".equals(dedupFields)) {
            deduplicatedData = data.dropDuplicates();
        } else {
            String[] dedupFieldArray = dedupFields.split(",");
            if ("csv".equalsIgnoreCase(fileExtension)) {
                deduplicatedData = data.dropDuplicates(dedupFieldArray);
            } else if ("txt".equalsIgnoreCase(fileExtension)) {
                String[] dedupColumnNames = new String[dedupFieldArray.length];
                for (int i = 0; i < dedupFieldArray.length; i++) {
                    dedupColumnNames[i] = "dedup_column_" + i;
                }
                deduplicatedData = data.dropDuplicates(dedupColumnNames);
            } else {
                throw new IllegalStateException("未知文件类型");
            }
        }

        String tempOutputDir = hdfsOutputPath + "_temp_" + UUID.randomUUID().toString();
        Path tempOutputPath = new Path(tempOutputDir);
        Path finalOutputPath = new Path(hdfsOutputPath);

        if ("csv".equalsIgnoreCase(fileExtension)) {
            deduplicatedData.coalesce(1)
                    .write()
                    .option("header", "true")
                    .csv(tempOutputDir);
        } else if ("txt".equalsIgnoreCase(fileExtension)) {
            if ("_ALL_FIELDS_".equals(dedupFields)) {
                String[] columns = deduplicatedData.columns();
                Column[] columnArray = new Column[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    columnArray[i] = functions.col(columns[i]);
                }
                deduplicatedData = deduplicatedData.select(functions.concat_ws(",", columnArray).alias("value"));
            }
            deduplicatedData.select("value").coalesce(1)
                    .write()
                    .text(tempOutputDir);
        }

        FileStatus[] partFiles = fs.globStatus(new Path(tempOutputDir + "/part-*"));
        if (partFiles != null && partFiles.length > 0) {
            fs.rename(partFiles[0].getPath(), finalOutputPath);
        }

//        fs.close();
        spark.close();
    }

    // 编码检测
    private String detectEncoding(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        byte[] bytes = baos.toByteArray();

        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return "UTF-8";
        }

        String[] encodings = {"UTF-8", "GBK", "GB2312", "Windows-1252"};
        for (String encoding : encodings) {
            try {
                String sample = new String(bytes, Charset.forName(encoding));
                if (isValidText(sample)) {
                    return encoding;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return "UTF-8";
    }

    // 文本有效性检查
    private boolean isValidText(String text) {
        for (char c : text.toCharArray()) {
            if (c >= 0xFFFD || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) {
                return false;
            }
        }
        return true;
    }
}