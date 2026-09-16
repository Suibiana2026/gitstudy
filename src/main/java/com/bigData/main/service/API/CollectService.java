package com.bigData.main.service.API;

import com.bigData.main.TestSocket.DeviceDataSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时数据采集编排服务。
 * <p>
 * 原先这套编排逻辑写在 {@code DataCollectionController} 里，导致智能体工具没法复用。
 * 抽出来后，「数据采集」页面接口和智能体的 {@code start_collect} 工具共用同一套实现，
 * 不会出现两条会各自漂移的代码路径。
 * <p>
 * 流程：解析数据源地址 -> 确保 TCP 采集服务端在监听 -> 起设备端线程上报数据
 * -> 等待服务端落库 HDFS -> 返回采集结果。
 */
@Service
public class CollectService {

    private static final Logger logger = LoggerFactory.getLogger(CollectService.class);

    /** 采集时长上限（秒），防止智能体发起超长采集把页面挂住 */
    public static final int MAX_DURATION = 60;
    /** 每秒上报条数上限 */
    public static final int MAX_ROWS_PER_SEC = 100;

    @Autowired
    private Server tcpCollectServer;

    /**
     * 执行一次 TCP 数据采集。
     *
     * @param taskId     任务 ID
     * @param sourceAddr 数据源地址，支持 host:port / tcp://host:port / 空（默认本机采集服务端）
     * @param duration   采集秒数
     * @param rowsPerSec 每秒上报条数
     * @return 响应体；{@code status} 为 success 或 error
     */
    public Map<String, Object> collectByTcp(String taskId, String sourceAddr, int duration, int rowsPerSec) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();

        if (duration < 1) {
            response.put("status", "error");
            response.put("taskId", taskId);
            response.put("message", "采集时间必须大于 0 秒");
            return response;
        }
        if (duration > MAX_DURATION) {
            duration = MAX_DURATION;
        }
        if (rowsPerSec < 1) {
            rowsPerSec = 5;
        }
        if (rowsPerSec > MAX_ROWS_PER_SEC) {
            rowsPerSec = MAX_ROWS_PER_SEC;
        }

        try {
            // 解析数据源地址，默认连接本机采集服务端
            String host = "127.0.0.1";
            int port = tcpCollectServer.getPort();
            if (sourceAddr != null && !sourceAddr.trim().isEmpty()) {
                String[] hp = parseHostPort(sourceAddr, port);
                host = hp[0];
                port = Integer.parseInt(hp[1]);
            }

            // 确保服务端处于监听状态
            if (!tcpCollectServer.isRunning()) {
                tcpCollectServer.startServer();
            }

            String safeTaskId = taskId == null ? "" : taskId.replaceAll("[^0-9A-Za-z]", "");
            String fileName = "socket_collect_" + (safeTaskId.isEmpty() ? System.currentTimeMillis() : safeTaskId)
                    + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

            logger.info("开始 Socket 采集，任务: {}, 数据源: {}:{}, 采集时长: {} 秒, 每秒 {} 条",
                    taskId, host, port, duration, rowsPerSec);

            // 启动设备客户端上报数据（真实 TCP 连接）
            final String targetHost = host;
            final int targetPort = port;
            final int finalDuration = duration;
            final int finalRowsPerSec = rowsPerSec;
            Thread deviceThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        DeviceDataSimulator.send(targetHost, targetPort, finalDuration, finalRowsPerSec, fileName);
                    } catch (Exception e) {
                        logger.error("设备端上报数据失败: {}", e.getMessage());
                    }
                }
            }, "device-simulator");
            deviceThread.setDaemon(true);
            deviceThread.start();

            // 等待服务端完成接收并落库
            long timeout = duration * 1000L + 30000L;
            Map<String, Object> record = tcpCollectServer.waitForRecord(fileName, timeout);

            if (record == null) {
                response.put("status", "error");
                response.put("taskId", taskId);
                response.put("message", "采集超时：无法连接数据源 " + host + ":" + port
                        + "，请确认采集服务端已启动（本机监听端口 " + tcpCollectServer.getPort() + "）");
                return response;
            }

            // 数据虽然收到了，但没能写进 HDFS 时必须如实报错，不能返回 success
            if (Boolean.FALSE.equals(record.get("uploadSuccess"))) {
                response.put("status", "error");
                response.put("taskId", taskId);
                response.put("fileName", record.get("fileName"));
                response.put("dataCount", record.get("dataCount"));
                response.put("message", record.get("error"));
                return response;
            }

            response.put("status", "success");
            response.put("uploadSuccess", true);
            response.put("taskId", taskId);
            response.put("source", host + ":" + port);
            response.put("duration", duration);
            response.put("rowsPerSec", rowsPerSec);
            response.put("fileName", record.get("fileName"));
            response.put("filePath", record.get("filePath"));
            response.put("fileSize", record.get("fileSize"));
            response.put("dataCount", record.get("dataCount"));
            response.put("serverStatus", tcpCollectServer.getStatus());

            logger.info("Socket 采集完成，文件: {}, 数据量: {} 条", record.get("fileName"), record.get("dataCount"));
            return response;

        } catch (Exception e) {
            logger.error("Socket 采集失败", e);
            response.clear();
            response.put("status", "error");
            response.put("taskId", taskId);
            response.put("message", e.getMessage());
            return response;
        }
    }

    /**
     * 采集服务端运行状态
     */
    public Map<String, Object> serverStatus() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("status", "success");
        response.put("server", tcpCollectServer.getStatus());
        return response;
    }

    /**
     * 从地址中解析出主机与端口，支持 ws://host:port、tcp://host:port、host:port 三种写法
     */
    private static String[] parseHostPort(String address, int defaultPort) {
        String s = address.trim();
        int schemeIdx = s.indexOf("://");
        if (schemeIdx >= 0) {
            s = s.substring(schemeIdx + 3);
        }
        int slashIdx = s.indexOf('/');
        if (slashIdx >= 0) {
            s = s.substring(0, slashIdx);
        }
        int colonIdx = s.lastIndexOf(':');
        if (colonIdx > 0) {
            String host = s.substring(0, colonIdx);
            try {
                int port = Integer.parseInt(s.substring(colonIdx + 1));
                return new String[]{host, String.valueOf(port)};
            } catch (NumberFormatException e) {
                return new String[]{host, String.valueOf(defaultPort)};
            }
        }
        return new String[]{s.isEmpty() ? "127.0.0.1" : s, String.valueOf(defaultPort)};
    }
}
