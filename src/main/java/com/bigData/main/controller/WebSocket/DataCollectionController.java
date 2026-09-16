package com.bigData.main.controller.WebSocket;

import com.bigData.main.controller.WebSocket.DataCollectionHandler;
import com.bigData.main.service.API.CollectService;
import com.bigData.main.service.API.Server;
import com.bigData.main.service.HDFS.UploadService;
import com.bigData.main.TestSocket.DeviceDataSimulator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/WebSocket")
public class DataCollectionController {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectionController.class);

    @Autowired
    private DataCollectionHandler dataCollectionHandler;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private Server tcpCollectServer;

    @Autowired
    private CollectService collectService;


    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulateDataCollection(
            @RequestBody Map<String, Object> params) {

        try {
            // 参数解析
            String taskId = (String) params.get("taskId");
            int duration = Integer.parseInt(params.get("duration").toString());

            logger.info("开始模拟数据采集，任务ID: {}, 持续时间: {}秒", taskId, duration);

            // 1. 生成CSV内容（每秒生成1条数据）
            int dataCount = duration + 100; // 假设每秒生成1条数据
            String csvContent = dataCollectionHandler.generateCsvContent(dataCount);

            // 2. 生成文件名
            String fileName = dataCollectionHandler.generateFileName(taskId);
            Path hdfsPath = new Path("/original_data/" + fileName);

            // 3. 保存到HDFS - 确保使用正确的HDFS服务
            Configuration conf = new Configuration();
            // 设置HDFS配置
            conf.set("fs.defaultFS", "hdfs://zyh120:8020");
            conf.set("dfs.replication", "1");

            try (FileSystem fs = FileSystem.newInstance(conf)) {
                // 确保/original_data目录存在
                if (!fs.exists(new Path("/original_data"))) {
                    fs.mkdirs(new Path("/original_data"));
                    logger.info("创建HDFS目录: /original_data");
                }

                // 写入文件到HDFS
                try (OutputStream os = fs.create(hdfsPath)) {
                    os.write(csvContent.getBytes());
                    logger.info("文件成功写入HDFS: {}", hdfsPath);
                }
            }

            // 4. 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("taskId", taskId);
            response.put("fileName", fileName);
            response.put("filePath", hdfsPath.toString());
            response.put("fileSize", csvContent.getBytes().length);
            response.put("dataCount", dataCount);

            logger.info("数据采集完成，生成文件: {}, 数据量: {}条", fileName, dataCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("数据采集失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 真实网络数据采集：设备端通过 TCP Socket 上报数据，服务端接收后落库 HDFS
     * <p>
     * 请求参数：taskId（任务ID）、wsUrl（数据源地址，如 127.0.0.1:9999）、duration（采集秒数）、rowsPerSec（每秒上报条数）
     */
    @PostMapping("/collect/tcp")
    public ResponseEntity<Map<String, Object>> collectByTcp(@RequestBody Map<String, Object> params) {
        String taskId = params.get("taskId") != null ? String.valueOf(params.get("taskId")) : ("task-" + System.currentTimeMillis());

        int duration;
        int rowsPerSec;
        try {
            duration = params.get("duration") != null ? Integer.parseInt(String.valueOf(params.get("duration"))) : 5;
            rowsPerSec = params.get("rowsPerSec") != null ? Integer.parseInt(String.valueOf(params.get("rowsPerSec"))) : 5;
        } catch (NumberFormatException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("taskId", taskId);
            errorResponse.put("message", "采集时间与每秒上报条数必须是数字");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
        String sourceAddr = params.get("wsUrl") != null ? String.valueOf(params.get("wsUrl")) : "";

        // 编排逻辑已抽到 CollectService，与智能体的 start_collect 工具共用同一套实现
        Map<String, Object> body = collectService.collectByTcp(taskId, sourceAddr, duration, rowsPerSec);
        return "success".equals(body.get("status"))
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 查询采集服务端运行状态
     */
    @GetMapping("/collect/status")
    public ResponseEntity<Map<String, Object>> collectServerStatus() {
        return ResponseEntity.ok(collectService.serverStatus());
    }
}