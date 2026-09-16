/**
 * 用于单独下载 HDFS /original_data 目录下的文件
 * DELETE 请求
 * 接口: "/HDFS/download/original_data/{fileName}"
 */

package com.bigData.main.controller.HDFS.OriginalData;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/HDFS/download")
public class DownloadFile1 {

    private static final Logger logger = LoggerFactory.getLogger(DownloadFile1.class);

    // 支持的常见文件后缀（可以根据需要扩展）
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(
            ".txt", ".csv", ".jpg", ".png", ".pdf", ".docx", ".xlsx", ".json"
    );

    @Autowired
    private FileSystem fileSystem;

    // 从配置文件中读取 HDFS 下载目录
    @Value("${hadoop.hdfs.original-data}")
    private String hdfsDownloadDir;

    @GetMapping("/original_data/{fileName}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        try {
            logger.info("收到文件下载请求: {}", fileName);

            // 1. 构造完整文件路径
            Path hdfsPath = new Path(hdfsDownloadDir + "/" + fileName);

            // 2. 检查文件是否存在并下载（如果文件名包含后缀）
            if (fileSystem.exists(hdfsPath) && !fileSystem.isDirectory(hdfsPath)) {
                downloadHdfsFile(hdfsPath, response);
                return;
            }

            // 3. 如果文件名不包含后缀，尝试查找匹配的文件
            if (!fileName.contains(".")) {
                hdfsPath = findMatchingFile(fileName);
                if (hdfsPath != null) {
                    downloadHdfsFile(hdfsPath, response);
                    return;
                }
            }

            // 4. 文件未找到
            logger.warn("文件不存在: {}", fileName);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found: " + fileName);

        } catch (IllegalArgumentException e) {
            logger.error("路径校验失败: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid path or parameter: " + e.getMessage());
        } catch (IOException e) {
            logger.error("下载文件时发生 IO 异常: {}", e.getMessage(), e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error: IO exception");
        } catch (Exception e) {
            logger.error("下载文件时发生未知异常: {}", e.getMessage(), e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error: " + e.getMessage());
        }
    }

    /**
     * 下载 HDFS 文件的核心方法
     */
    private void downloadHdfsFile(Path hdfsPath, HttpServletResponse response) throws IOException {
        try (FSDataInputStream in = fileSystem.open(hdfsPath);
             OutputStream out = response.getOutputStream()) {

            // 获取文件状态并设置响应头
            FileStatus status = fileSystem.getFileStatus(hdfsPath);
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            String encodedFileName = URLEncoder.encode(hdfsPath.getName(), StandardCharsets.UTF_8.name())
                    .replace("+", "%20"); // 处理空格
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setHeader("Content-Length", String.valueOf(status.getLen()));

            // 流式传输文件内容
            IOUtils.copyBytes(in, out, 4096, false);
            logger.info("文件下载成功: {}", hdfsPath);
        }
    }

    /**
     * 查找匹配的文件（不含后缀的情况）
     */
    private Path findMatchingFile(String fileName) throws IOException {
        FileStatus[] files = fileSystem.listStatus(new Path(hdfsDownloadDir));
        for (FileStatus file : files) {
            if (!file.isDirectory()) {
                String hdfsFileName = file.getPath().getName();
                if (hdfsFileName.startsWith(fileName + ".")) {
                    String extension = hdfsFileName.substring(fileName.length());
                    if (SUPPORTED_EXTENSIONS.contains(extension)) {
                        logger.info("找到匹配的文件: {}", hdfsFileName);
                        return file.getPath();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpServletResponse response, int status, String message) {
        try {
            response.sendError(status, message);
        } catch (IOException e) {
            logger.error("发送错误响应失败: {}", e.getMessage(), e);
        }
    }
}