package com.bigData.main.service.API;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TCP Socket 数据采集服务端
 * <p>
 * 协议：客户端连接后先发送一行「文件名」（以 \n 结束），随后发送文件数据字节流，
 * 数据发送完毕后发送结束标记 "END_OF_FILE"（或直接关闭输出流）。
 * 服务端接收完成后将数据文件上传到 HDFS 的 /original_data 目录，并回执确认消息。
 * <p>
 * 服务端随 Spring 容器启动自动监听 9999 端口，支持多个客户端并发上报。
 */
@Component
public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private static final byte[] END_OF_FILE = "END_OF_FILE".getBytes(StandardCharsets.UTF_8);
    private static final int PORT = 9999;

    @Autowired(required = false)
    private FileSystem fileSystem;

    @Value("${hadoop.hdfs.original-data:/original_data}")
    private String originalDataPath;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    private final AtomicInteger connectedCount = new AtomicInteger(0);
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    private final AtomicLong receivedBytes = new AtomicLong(0L);

    private volatile String lastFileName = "";
    private volatile String lastHdfsPath = "";

    /** 采集记录：文件名 -> 详情（供接口按任务查询采集结果） */
    private final Map<String, Map<String, Object>> receivedRecords =
            Collections.synchronizedMap(new LinkedHashMap<String, Map<String, Object>>());

    @PostConstruct
    public void autoStart() {
        startServer();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warn("关闭 TCP 采集服务端时发生异常: {}", e.getMessage());
        }
        logger.info("【TCP采集服务端】已停止");
    }

    /**
     * 启动服务端监听（后台守护线程，随应用启动自动调用，也可手动调用）
     */
    public synchronized void startServer() {
        if (running) {
            return;
        }
        running = true;
        acceptThread = new Thread(this::acceptLoop, "tcp-collect-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        try (ServerSocket ss = new ServerSocket(PORT)) {
            serverSocket = ss;
            logger.info("【TCP采集服务端】启动成功，监听端口 {}", PORT);
            while (running) {
                try {
                    Socket socket = ss.accept();
                    connectedCount.incrementAndGet();
                    logger.info("【TCP采集服务端】客户端接入: {}", socket.getRemoteSocketAddress());
                    Thread worker = new Thread(() -> handleClient(socket), "tcp-collect-worker");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException e) {
                    if (running) {
                        logger.error("【TCP采集服务端】接收连接异常: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            running = false;
            logger.error("【TCP采集服务端】端口 {} 启动失败: {}", PORT, e.getMessage());
        }
    }

    /**
     * 处理单个客户端的文件上报
     */
    private void handleClient(Socket socket) {
        String fileName = null;
        File tmpFile = null;
        try (Socket s = socket;
             BufferedInputStream in = new BufferedInputStream(s.getInputStream());
             OutputStream out = s.getOutputStream()) {

            // 1. 读取第一行：文件名
            ByteArrayOutputStream nameBuffer = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    nameBuffer.write(b);
                }
            }
            fileName = new String(nameBuffer.toByteArray(), StandardCharsets.UTF_8).trim();
            if (fileName.isEmpty()) {
                fileName = "socket_data_" + System.currentTimeMillis() + ".csv";
            }
            logger.info("【TCP采集服务端】开始接收文件: {}", fileName);

            // 2. 读取数据直到结束标记或流结束
            File tmpDir = new File(System.getProperty("java.io.tmpdir"), "bd_socket_recv");
            if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                logger.warn("【TCP采集服务端】临时目录创建失败: {}", tmpDir.getAbsolutePath());
            }
            tmpFile = new File(tmpDir, System.currentTimeMillis() + "_" + fileName);

            long totalBytes = 0L;
            int lineCount = 0;
            byte[] buffer = new byte[8192];
            int matched = 0;
            boolean finished = false;

            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                int n;
                while (!finished && (n = in.read(buffer)) != -1) {
                    for (int i = 0; i < n; i++) {
                        byte cur = buffer[i];
                        if (cur == END_OF_FILE[matched]) {
                            matched++;
                            if (matched == END_OF_FILE.length) {
                                finished = true;
                                break;
                            }
                        } else {
                            if (matched > 0) {
                                fos.write(END_OF_FILE, 0, matched);
                                totalBytes += matched;
                                matched = 0;
                            }
                            if (cur == END_OF_FILE[0]) {
                                matched = 1;
                            } else {
                                fos.write(cur);
                                totalBytes++;
                                if (cur == '\n') {
                                    lineCount++;
                                }
                            }
                        }
                    }
                }
            }

            logger.info("【TCP采集服务端】文件接收完成: {}, 数据量 {} bytes", fileName, totalBytes);

            // 3. 上传到 HDFS
            String uploadedPath = uploadFileToHDFS(tmpFile);
            boolean uploadOk = uploadedPath != null;
            String hdfsPath = uploadOk ? uploadedPath : (originalDataPath + "/" + fileName);

            // 4. 记录采集结果（带上传成败标记，供接口如实反馈）
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("fileName", fileName);
            record.put("filePath", hdfsPath);
            record.put("fileSize", totalBytes);
            record.put("dataCount", Math.max(lineCount - 1, 0));
            record.put("receiveTime", System.currentTimeMillis());
            record.put("uploadSuccess", uploadOk);
            if (!uploadOk) {
                record.put("error", "数据已通过 Socket 接收，但写入 HDFS 失败，请检查 HDFS 连接状态");
            }
            receivedRecords.put(fileName, record);

            receivedCount.incrementAndGet();
            receivedBytes.addAndGet(totalBytes);
            lastFileName = fileName;
            lastHdfsPath = hdfsPath;

            // 5. 回执
            String ack = "SOCKET_RECV_OK " + fileName + " " + totalBytes;
            out.write(ack.getBytes(StandardCharsets.UTF_8));
            out.flush();
            logger.info("【TCP采集服务端】已回执客户端: {}", ack);

        } catch (IOException e) {
            logger.error("【TCP采集服务端】接收文件失败: {}", e.getMessage());
        } finally {
            if (tmpFile != null && tmpFile.exists() && !tmpFile.delete()) {
                logger.warn("【TCP采集服务端】临时文件删除失败: {}", tmpFile.getAbsolutePath());
            }
        }
    }

    /**
     * 将接收到的本地文件上传到 HDFS 的原始数据目录。
     *
     * @return 上传成功返回 HDFS 目标路径；失败返回 null
     * （原先无论成败都返回路径，导致接口报成功但 HDFS 里没有文件）
     */
    private String uploadFileToHDFS(File localFile) {
        String target = originalDataPath + "/" + localFile.getName().substring(localFile.getName().indexOf('_') + 1);
        try {
            if (fileSystem == null) {
                logger.error("【TCP采集服务端】FileSystem 未初始化，HDFS 上传失败");
                return null;
            }
            Path src = new Path(localFile.getAbsolutePath());
            Path dst = new Path(target);
            fileSystem.copyFromLocalFile(false, true, src, dst);
            logger.info("【TCP采集服务端】文件已上传 HDFS: {}", target);
            return target;
        } catch (IOException e) {
            logger.error("【TCP采集服务端】上传 HDFS 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 等待指定文件名的采集记录出现（供采集接口同步返回结果）
     *
     * @param fileName  文件名关键字
     * @param timeoutMs 超时毫秒
     * @return 采集记录，超时返回 null
     */
    public Map<String, Object> waitForRecord(String fileName, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> record = receivedRecords.get(fileName);
            if (record != null) {
                return record;
            }
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * 服务端运行状态
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running);
        status.put("port", PORT);
        status.put("connectedCount", connectedCount.get());
        status.put("receivedCount", receivedCount.get());
        status.put("receivedBytes", receivedBytes.get());
        status.put("lastFileName", lastFileName);
        status.put("lastHdfsPath", lastHdfsPath);
        return status;
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return PORT;
    }

    public int getReceivedCount() {
        return receivedCount.get();
    }
}
