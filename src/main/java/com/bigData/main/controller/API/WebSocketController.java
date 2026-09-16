package com.bigData.main.controller.API;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/HDFS")
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    @Autowired
    private FileSystem hdfs;

    @Value("${hadoop.hdfs.original-data}")
    private String hdfsWebsocketPath;

    @PostMapping("/collect/websocket")
    public ResponseEntity<Map<String, Object>> collectWebSocket(
            @RequestBody Map<String, Object> requestData,
            HttpServletRequest request) {
        try {
            // 记录请求来源和数据
            String clientIp = request.getRemoteAddr();
            logger.info("收到 WebSocket 请求，来源 IP: {}, 数据: {}", clientIp, requestData);

            // 提取 taskId
            String taskId = (String) requestData.get("taskId");
            if (taskId == null || taskId.trim().isEmpty()) {
                logger.error("taskId 为空，请求数据: {}", requestData);
                throw new IllegalArgumentException("taskId 不能为空");
            }

            // 提取 wsUrl 并解析设备 IP
            String wsUrl = (String) requestData.get("wsUrl");
            if (wsUrl == null) {
                logger.error("wsUrl 为空，请求数据: {}", requestData);
                throw new IllegalArgumentException("wsUrl 不能为空");
            }
            String deviceIp = extractIpFromUrl(wsUrl);

            // 提取 data 并安全转换为 Map
            Object dataObj = requestData.get("data");
            if (!(dataObj instanceof Map)) {
                logger.error("data 不是 Map 类型，请求数据: {}", requestData);
                throw new IllegalArgumentException("data 必须是 Map 类型");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) dataObj;

            // 生成 HDFS 文件路径，直接存储到 /original_data/
            String fileName = String.format("%s/%s.json", hdfsWebsocketPath, deviceIp.replace(".", "_"));
            Path hdfsPath = new Path(fileName);

            // 确保父目录存在
            Path parentDir = hdfsPath.getParent();
            if (!hdfs.exists(parentDir)) {
                hdfs.mkdirs(parentDir);
                logger.info("创建目录: {}", parentDir.toString());
            }

            // 写入 HDFS
            try (FSDataOutputStream out = hdfs.create(hdfsPath, true)) {
                out.writeUTF(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data));
            }

            // 返回文件信息
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", hdfsPath.getName()); // 只返回文件名，如 10_5_0_66.json
            fileInfo.put("type", "json");
            fileInfo.put("size", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data).length());
            fileInfo.put("modifyTime", System.currentTimeMillis());
            fileInfo.put("path", fileName); // 完整路径，如 /original_data/10_5_0_66.json

            Map<String, Object> response = new HashMap<>();
            response.put("files", Collections.singletonList(fileInfo));
            logger.info("WebSocket 数据处理成功，taskId: {}, 文件: {}", taskId, fileName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("WebSocket 处理失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    private String extractIpFromUrl(String wsUrl) {
        String cleanedUrl = wsUrl.replace("ws://", "").replace("wss://", "");
        int portIndex = cleanedUrl.indexOf(":");
        if (portIndex != -1) {
            return cleanedUrl.substring(0, portIndex);
        }
        return cleanedUrl;
    }
}