package com.bigData.main.service.Agent;

import com.bigData.main.service.API.CollectService;
import com.bigData.main.service.API.SalesService;
import com.bigData.main.service.API.Server;
import com.bigData.main.service.MapReduce.MapReduceService;
import com.bigData.main.service.MySQL.StatisticsImportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体的业务工具集。
 * <p>
 * 把项目已有的四个业务模块（数据查询 / 文件管理 / 离线计算 / 实时采集）
 * 包装成大模型可调用的 function tools，让模型自己决定调用哪个，
 * 而不是把业务逻辑硬编码进提示词。
 */
@Service
public class AgentTools {

    private static final Logger logger = LoggerFactory.getLogger(AgentTools.class);

    @Value("${hadoop.fs.defaultFS:hdfs://zyh120:8020}")
    private String hdfsUri;

    @Autowired
    private SalesService salesService;

    @Autowired
    private MapReduceService mapReduceService;

    @Autowired
    private Server collectServer;

    @Autowired
    private CollectService collectService;

    @Autowired
    private StatisticsImportService statisticsImportService;

    /** 允许被上传的本地目录（相对路径按项目根目录解析），超出此目录一律拒绝 */
    @Value("${ai.agent.upload-root:uploads}")
    private String uploadRoot;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成交给大模型的 tools 定义（OpenAI / DashScope 兼容格式）
     */
    public List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>();

        tools.add(function(
                "query_sales_data",
                "查询汽车销售数据。支持按年（如 2025）、年月（如 2025-03）或完整日期（如 2025-03-15）查询，"
                        + "返回车型维度的销量、库存与市场趋势汇总。"
                        + "当用户询问销量、排名、库存、某段时间的销售情况时，必须调用此工具获取真实数据，不要凭空回答。",
                props(
                        prop("saleDate", "string", "查询区间，格式 YYYY 或 YYYY-MM 或 YYYY-MM-DD，例如 2025-03")),
                new String[]{"saleDate"}));

        tools.add(function(
                "generate_sales_plan",
                "基于历史销售数据生成完整的三月期销售方案，包含销售目标、销售策略、预算估算与执行时间表。"
                        + "当用户要求制定或生成销售计划、销售方案、营销策略时调用。",
                props(
                        prop("saleDate", "string", "方案依据的销售区间，格式 YYYY 或 YYYY-MM 或 YYYY-MM-DD")),
                new String[]{"saleDate"}));

        tools.add(function(
                "list_hdfs_files",
                "列出 HDFS 中的数据文件。用于回答有哪些数据文件、数据存储量有多大这类问题。",
                props(
                        prop("zone", "string", "目录区域：original 表示原始数据 /original_data，processed 表示处理结果 /processed_data")),
                new String[]{"zone"}));

        tools.add(function(
                "run_mapreduce_job",
                "在 HDFS 数据上提交离线计算作业。jobType 为 wordcount 时做词频统计，为 dedup 时按字段去重。"
                        + "当用户要求跑计算任务、做词频统计、按字段去重时调用。",
                props(
                        prop("jobType", "string", "作业类型：wordcount 词频统计，dedup 按字段去重"),
                        prop("field", "string", "参与计算或去重的字段名，如 model、region")),
                new String[]{"jobType"}));

        tools.add(function(
                "get_collect_status",
                "查询实时数据采集服务（TCP Socket）的运行状态，包括监听端口、已连接设备数、已接收数据条数。"
                        + "当用户询问采集任务状态、数据源连接情况时调用。",
                props(),
                new String[]{}));

        tools.add(function(
                "start_collect",
                "发起一次实时数据采集：通过 TCP Socket 从数据源采集数据，接收后写入 HDFS 原始数据目录。"
                        + "当用户要求采集数据、跑一次采集任务、模拟设备上报时调用。采集时长默认 5 秒、最长 60 秒。",
                props(
                        prop("sourceAddr", "string", "数据源地址，格式 host:port，如 127.0.0.1:9999；留空则使用本机采集服务端"),
                        prop("duration", "integer", "采集时长（秒），默认 5，最大 60"),
                        prop("rowsPerSec", "integer", "每秒上报条数，默认 5")),
                new String[]{}));

        tools.add(function(
                "import_statistics_to_db",
                "对 HDFS 上的 CSV 数据按指定列做分组统计，并把结果写入 MySQL 大屏数据表（品牌销量排行）。"
                        + "这是「采集 -> 清洗统计 -> 大屏展示」闭环的最后一步。"
                        + "当用户要求统计、分析、统计入库、刷新大屏数据时调用。",
                props(
                        prop("filePath", "string", "HDFS 文件路径，如 /original_data/socket_collect_xxx.csv"),
                        prop("field", "string", "参与统计的列名，如 brand_name；若文件中不存在会自动回退到第二列"),
                        prop("topN", "integer", "保留条数，默认 10")),
                new String[]{"filePath"}));

        tools.add(function(
                "restore_screen_data",
                "把大屏数据恢复成备份表中的原始数据。当用户要求恢复大屏数据、撤销上一次统计入库时调用。",
                props(),
                new String[]{}));

        tools.add(function(
                "upload_local_file",
                "把服务器本地目录里的文件上传到 HDFS。出于安全考虑，只允许上传系统配置的「上传目录」内的文件，"
                        + "用户需要先把文件放进该目录。当用户要求上传文件、导入数据文件时调用。",
                props(
                        prop("localPath", "string", "上传目录内的文件名，留空表示上传该目录下全部文件"),
                        prop("zone", "string", "目标区域：original 表示 /original_data，processed 表示 /processed_data")),
                new String[]{}));

        return tools;
    }

    /**
     * 执行指定工具，返回给大模型观察的文本结果。
     * 任何异常都会被转成可读文本，避免整轮对话中断。
     */
    public String execute(String toolName, String argumentsJson) {
        Map<String, Object> args = parseArgs(argumentsJson);
        long start = System.currentTimeMillis();
        try {
            if ("query_sales_data".equals(toolName)) {
                return salesService.summarizeSales(str(args, "saleDate"));
            }
            if ("generate_sales_plan".equals(toolName)) {
                return salesService.generateSalesTemplate(str(args, "saleDate"));
            }
            if ("list_hdfs_files".equals(toolName)) {
                return listHdfsFiles(str(args, "zone"));
            }
            if ("run_mapreduce_job".equals(toolName)) {
                return runMapReduceJob(str(args, "jobType"), str(args, "field"));
            }
            if ("get_collect_status".equals(toolName)) {
                return collectStatus();
            }
            if ("start_collect".equals(toolName)) {
                return startCollect(str(args, "sourceAddr"), args.get("duration"), args.get("rowsPerSec"));
            }
            if ("import_statistics_to_db".equals(toolName)) {
                return importStatisticsToDb(str(args, "filePath"), str(args, "field"), args.get("topN"));
            }
            if ("restore_screen_data".equals(toolName)) {
                return restoreScreenData();
            }
            if ("upload_local_file".equals(toolName)) {
                return uploadLocalFile(str(args, "localPath"), str(args, "zone"));
            }
            return "未知工具：" + toolName;
        } catch (Exception e) {
            logger.error("工具执行失败: " + toolName, e);
            return "工具执行失败（" + toolName + "）：" + e.getMessage();
        } finally {
            logger.info("工具 " + toolName + " 执行耗时 " + (System.currentTimeMillis() - start) + " ms");
        }
    }

    // ------------------------------------------------------------------
    // 具体工具实现
    // ------------------------------------------------------------------

    private String listHdfsFiles(String zone) {
        String dir = ("processed".equalsIgnoreCase(zone) || "2".equals(zone)) ? "/processed_data" : "/original_data";
        FileSystem fs = null;
        try {
            fs = openFileSystem();
            Path path = new Path(dir);
            if (!fs.exists(path)) {
                return "HDFS 目录 " + dir + " 不存在，可能尚未上传或采集过数据。";
            }

            List<String> files = new ArrayList<String>();
            long totalSize = 0L;
            for (FileStatus status : fs.listStatus(path)) {
                if (status.isDirectory()) {
                    continue;
                }
                files.add(status.getPath().getName() + "（" + formatSize(status.getLen()) + "）");
                totalSize += status.getLen();
            }

            if (files.isEmpty()) {
                return "HDFS 目录 " + dir + " 存在，但里面没有任何数据文件。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("目录 ").append(dir).append(" 共 ").append(files.size())
                    .append(" 个文件，合计 ").append(formatSize(totalSize)).append("：\n");

            int limit = Math.min(files.size(), 30);
            for (int i = 0; i < limit; i++) {
                sb.append(i + 1).append(". ").append(files.get(i)).append("\n");
            }
            if (files.size() > limit) {
                sb.append("...（其余 ").append(files.size() - limit).append(" 个文件未列出）");
            }
            return sb.toString();
        } catch (Exception e) {
            return "无法访问 HDFS（" + hdfsUri + "）：" + e.getMessage();
        } finally {
            closeQuietly(fs);
        }
    }

    private String runMapReduceJob(String jobType, String field) {
        List<String> paths;
        FileSystem fs = null;
        try {
            fs = openFileSystem();
            paths = collectFilePaths(fs, "/original_data");
        } catch (Exception e) {
            return "无法访问 HDFS（" + hdfsUri + "）：" + e.getMessage();
        } finally {
            closeQuietly(fs);
        }

        if (paths.isEmpty()) {
            return "HDFS /original_data 下没有数据文件，无法提交计算作业。请先上传数据或执行一次数据采集。";
        }

        String fieldName = (field == null || field.trim().isEmpty()) ? "model" : field.trim();
        String filePaths = join(paths);

        if ("dedup".equalsIgnoreCase(jobType)) {
            String output = mapReduceService.duplicationEliminating(filePaths, fieldName);
            return "去重作业执行成功。参与文件 " + paths.size() + " 个，去重字段：" + fieldName
                    + "，结果输出路径：" + output;
        }

        Map<String, Map<String, Integer>> result = mapReduceService.wordFrequencyStatistics(filePaths, fieldName);
        int groups = result == null ? 0 : result.size();
        return "词频统计作业执行成功。参与文件 " + paths.size() + " 个，统计字段：" + fieldName
                + "，返回结果分组数：" + groups;
    }

    private String collectStatus() {
        try {
            Map<String, Object> status = collectServer.getStatus();
            StringBuilder sb = new StringBuilder("数据采集服务（TCP Socket）当前状态：\n");
            for (Map.Entry<String, Object> entry : status.entrySet()) {
                sb.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "采集服务状态获取失败：" + e.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // 采集 / 清洗 / 上传 工具实现
    // ------------------------------------------------------------------

    private String startCollect(String sourceAddr, Object durationArg, Object rowsPerSecArg) {
        int duration = toInt(durationArg, 5);
        int rowsPerSec = toInt(rowsPerSecArg, 5);
        String addr = (sourceAddr == null || sourceAddr.trim().isEmpty())
                ? "127.0.0.1:9999" : sourceAddr.trim();

        Map<String, Object> result = collectService.collectByTcp(
                "agent-" + System.currentTimeMillis(), addr, duration, rowsPerSec);

        if (!"success".equals(result.get("status"))) {
            return "采集失败：" + result.get("message");
        }
        return "采集成功。\n"
                + "- 数据源：" + result.get("source") + "\n"
                + "- 采集时长：" + result.get("duration") + " 秒\n"
                + "- 生成文件：" + result.get("fileName") + "\n"
                + "- HDFS 路径：" + result.get("filePath") + "\n"
                + "- 数据条数：" + result.get("dataCount") + " 条\n"
                + "- 文件大小：" + formatSize(toLong(result.get("fileSize"), 0L));
    }

    private String importStatisticsToDb(String filePath, String field, Object topNArg) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "需要先指定要统计的 HDFS 文件路径，例如 /original_data/socket_collect_xxx.csv。"
                    + "可以先用文件列表工具确认有哪些文件。";
        }
        try {
            Map<String, Object> result = statisticsImportService.importStatisticsToDatabase(
                    filePath.trim(), field, toInt(topNArg, 10));

            StringBuilder sb = new StringBuilder();
            sb.append("统计入库完成，共写入 ").append(result.get("importCount")).append(" 条，大屏数据已更新。\n");
            sb.append("- 使用字段：").append(result.get("field"));
            if (Boolean.TRUE.equals(result.get("fallbackField"))) {
                sb.append("（指定字段在文件中不存在，已自动回退到第二列）");
            }
            sb.append("\n- 源数据行数：").append(result.get("sourceRows")).append("\n");
            sb.append("- 统计结果：\n");

            Object items = result.get("items");
            if (items instanceof List) {
                for (Object item : (List<?>) items) {
                    if (item instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) item;
                        sb.append("    ").append(m.get("name")).append(" -> ").append(m.get("value")).append("\n");
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "统计入库失败：" + e.getMessage();
        }
    }

    private String restoreScreenData() {
        try {
            Map<String, Object> result = statisticsImportService.restoreFromBackup();
            return String.valueOf(result.get("message"));
        } catch (Exception e) {
            return "恢复失败：" + e.getMessage();
        }
    }

    private String uploadLocalFile(String localPath, String zone) {
        File root;
        try {
            root = resolveUploadRoot().getCanonicalFile();
        } catch (IOException e) {
            return "无法解析上传目录：" + e.getMessage();
        }
        if (!root.isDirectory()) {
            return "上传目录不存在：" + root.getAbsolutePath()
                    + "。请先建好该目录并把要上传的文件放进去"
                    + "（目录可在 application.yml 的 ai.agent.upload-root 配置）。";
        }

        List<File> targets = new ArrayList<File>();
        if (localPath == null || localPath.trim().isEmpty() || "*".equals(localPath.trim())) {
            File[] all = root.listFiles();
            if (all != null) {
                for (File f : all) {
                    if (f.isFile()) {
                        targets.add(f);
                    }
                }
            }
            if (targets.isEmpty()) {
                return "上传目录 " + root.getAbsolutePath() + " 里没有任何文件，请先把文件放进去。";
            }
        } else {
            File candidate;
            try {
                candidate = new File(root, localPath.trim()).getCanonicalFile();
            } catch (IOException e) {
                return "无法解析文件路径：" + localPath;
            }
            // 安全边界：不允许跳出上传目录，避免被用来读取本机任意文件
            if (!candidate.getPath().equals(root.getPath())
                    && !candidate.getPath().startsWith(root.getPath() + File.separator)) {
                return "出于安全限制，只能上传 " + root.getAbsolutePath() + " 目录内的文件。";
            }
            if (!candidate.isFile()) {
                return "文件不存在：" + candidate.getAbsolutePath();
            }
            targets.add(candidate);
        }

        String dir = ("processed".equalsIgnoreCase(zone) || "2".equals(zone))
                ? "/processed_data" : "/original_data";

        FileSystem fs = null;
        try {
            fs = openFileSystem();
            if (!fs.exists(new Path(dir))) {
                fs.mkdirs(new Path(dir));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("上传目标目录：").append(dir).append("\n");

            List<String> failed = new ArrayList<String>();
            int okCount = 0;

            for (File f : targets) {
                Path dst = new Path(dir + "/" + f.getName());
                try {
                    fs.copyFromLocalFile(false, true, new Path(f.getAbsolutePath()), dst);
                    okCount++;
                    sb.append("- 成功：").append(f.getName())
                            .append("（").append(formatSize(f.length())).append("）\n");
                } catch (Exception e) {
                    failed.add(f.getName() + "：" + e.getMessage());
                }
            }

            sb.append("共 ").append(targets.size()).append(" 个文件，成功 ").append(okCount).append(" 个");
            if (!failed.isEmpty()) {
                sb.append("，失败 ").append(failed.size()).append(" 个 -> ").append(join(failed));
            }
            return sb.toString();
        } catch (Exception e) {
            return "上传失败：" + e.getMessage();
        } finally {
            closeQuietly(fs);
        }
    }

    private File resolveUploadRoot() {
        File root = new File(uploadRoot);
        if (!root.isAbsolute()) {
            root = new File(System.getProperty("user.dir"), uploadRoot);
        }
        return root;
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private FileSystem openFileSystem() throws Exception {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", hdfsUri);
        conf.set("dfs.replication", "1");
        // 用 newInstance 创建【独立】实例：本方法用完会 close，
        // 若用 FileSystem.get() 会拿到全局共享单例，一 close 就废掉整个应用的 HDFS 客户端。
        return FileSystem.newInstance(conf);
    }

    private List<String> collectFilePaths(FileSystem fs, String dir) throws Exception {
        List<String> result = new ArrayList<String>();
        Path path = new Path(dir);
        if (!fs.exists(path)) {
            return result;
        }
        for (FileStatus status : fs.listStatus(path)) {
            if (!status.isDirectory()) {
                result.add(status.getPath().toString());
            }
        }
        return result;
    }

    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(argumentsJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            logger.warn("工具参数解析失败，按空参数处理：" + argumentsJson);
            return new LinkedHashMap<String, Object>();
        }
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(v);
        }
        return sb.toString();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return round1(bytes / 1024d) + " KB";
        }
        return round1(bytes / (1024d * 1024d)) + " MB";
    }

    private static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static void closeQuietly(FileSystem fs) {
        if (fs != null) {
            try {
                fs.close();
            } catch (Exception ignored) {
                // 关闭失败不影响业务结果
            }
        }
    }

    private static Map<String, Object> prop(String name, String type, String description) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("name", name);
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    /** 把 prop(...) 的列表转成 JSON Schema 的 properties 结构 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> props(Map<String, Object>... defs) {
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        for (Map<String, Object> def : defs) {
            Map<String, Object> single = new LinkedHashMap<String, Object>();
            single.put("type", def.get("type"));
            single.put("description", def.get("description"));
            properties.put((String) def.get("name"), single);
        }
        return properties;
    }

    private static Map<String, Object> function(String name, String description,
                                                Map<String, Object> properties, String[] required) {
        Map<String, Object> fn = new LinkedHashMap<String, Object>();
        fn.put("name", name);
        fn.put("description", description);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", new ArrayList<String>(Arrays.asList(required)));

        fn.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<String, Object>();
        tool.put("type", "function");
        tool.put("function", fn);
        return tool;
    }
}
