package com.bigData.main.controller.HDFS.ProcessedData;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.io.IOUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/HDFS/download")
public class DownloadFile2 {
    // HDFS 的 URI，根据你的集群配置修改
    private static final String HDFS_URI = "hdfs://zyh120:8020";
    // HDFS 的下载目录
    private static final String HDFS_DOWNLOAD_DIR = "/processed_data";
    // 支持的常见文件后缀（可以根据需要扩展）
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(
            ".txt", ".csv", ".jpg", ".png", ".pdf", ".docx", ".xlsx"
    );

    @GetMapping("/processed_data/{fileName}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        try {
            // 1. 配置 HDFS 客户端
            Configuration conf = new Configuration();
            conf.set("fs.defaultFS", HDFS_URI);
            conf.setInt("dfs.client.socket-timeout", 300_000);

            // 2. 尝试直接下载文件（如果文件名包含后缀）
            Path hdfsPath = new Path(HDFS_DOWNLOAD_DIR + "/" + fileName);
            try (FileSystem fs = FileSystem.newInstance(URI.create(HDFS_URI), conf)) {
                if (fs.exists(hdfsPath) && !fs.isDirectory(hdfsPath)) {
                    downloadHdfsFile(fs, hdfsPath, response);
                    return;
                }

                // 3. 如果文件不存在，且文件名不包含后缀，尝试查找匹配的文件
                if (!fileName.contains(".")) {
                    FileStatus[] files = fs.listStatus(new Path(HDFS_DOWNLOAD_DIR));
                    for (FileStatus file : files) {
                        if (!file.isDirectory()) {
                            String hdfsFileName = file.getPath().getName();
                            // 检查文件名是否以 fileName 开头，并且后缀在支持的列表中
                            if (hdfsFileName.startsWith(fileName + ".")) {
                                String extension = hdfsFileName.substring(fileName.length());
                                if (SUPPORTED_EXTENSIONS.contains(extension)) {
                                    hdfsPath = file.getPath();
                                    downloadHdfsFile(fs, hdfsPath, response);
                                    return;
                                }
                            }
                        }
                    }
                }

                // 4. 如果没有找到匹配的文件，返回 404
                throw new FileNotFoundException("File not found: " + fileName);
            }

        } catch (IllegalArgumentException e) {
            // 路径或参数不合法
            System.err.println("路径校验失败: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
        } catch (FileNotFoundException e) {
            // 文件不存在
            System.err.println("文件未找到: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404
        } catch (Exception e) {
            // 其他服务器错误
            System.err.println("服务器错误: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
        }
    }

    // 下载 HDFS 文件的通用方法
    private void downloadHdfsFile(FileSystem fs, Path hdfsPath, HttpServletResponse response) throws Exception {
        try (FSDataInputStream in = fs.open(hdfsPath);
             OutputStream out = response.getOutputStream()) {

            // 获取文件状态并设置响应头
            FileStatus status = fs.getFileStatus(hdfsPath);
            response.setContentType("application/octet-stream");
            // 对文件名进行 URL 编码，防止中文或特殊字符导致乱码
            String encodedFileName = URLEncoder.encode(hdfsPath.getName(), StandardCharsets.UTF_8.toString());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setHeader("Content-Length", String.valueOf(status.getLen()));

            // 流式传输文件内容
            IOUtils.copyBytes(in, out, 4096, false);
        }
    }
}