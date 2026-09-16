package com.bigData.main.controller.HDFS.ProcessedData;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/HDFS")
public class PreviewFile2 {

    @Value("${hadoop.fs.defaultFS}")
    private String hdfsDefaultFS;

    @Value("${hadoop.user.name}")
    private String hdfsDefaultUser;
    private static final Logger logger = LoggerFactory.getLogger(PreviewFile2.class);

    @GetMapping("/previewProcessedData/{fileName}")
    public ResponseEntity<?> previewFile(@PathVariable String fileName) {
        // 解码URL中的文件名（处理中文等特殊字符）
        fileName = UriUtils.decode(fileName, StandardCharsets.UTF_8.name());

        // 打印文件名到日志
        logger.info("后端预览文件名: {}", fileName);

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", hdfsDefaultFS);

        try (FileSystem fs = FileSystem.newInstance(URI.create(hdfsDefaultFS), conf, hdfsDefaultUser)) {
            Path filePath = new Path("/processed_data/" + fileName);

            // 打印文件路径
            logger.info("文件路径: {}", filePath.toString());

            // 如果文件不存在，尝试查找带有扩展名的文件
            if (!fs.exists(filePath)) {
                logger.warn("文件不存在: {}", filePath.toString());
                fileName = findFileWithExtension(fs, fileName);
                if (fileName == null) {
                    logger.warn("未找到任何匹配的文件: {}", fileName);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文件不存在");
                }
                filePath = new Path("/processed_data/" + fileName);
                logger.info("找到匹配的文件: {}", filePath.toString());
            }

            // 根据文件类型返回不同的内容
            String fileType = getFileType(fileName);
            logger.info("文件类型: {}", fileType);
            switch (fileType) {
                case "txt":
                case "csv":
                    return previewTextFile(fs, filePath);
                case "jpg":
                case "png":
                case "gif":
                    return previewImage(fs, filePath);
                case "pdf":
                    return previewPDF(fs, filePath);
                case "mp3":
                    return previewAudio(fs, filePath);
                case "mp4":
                    return previewVideo(fs, filePath);
                default:
                    logger.warn("不支持的文件格式: {}", fileType);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("不支持的文件格式");
            }
        } catch (Exception e) {
            logger.error("预览文件失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("服务器错误");
        }
    }

    // 根据文件名获取文件类型
    private String getFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex != -1) ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }

    // 查找带有扩展名的文件
    private String findFileWithExtension(FileSystem fs, String baseName) {
        String[] possibleExtensions = {"txt", "csv", "jpg", "png", "pdf", "mp3", "mp4"};
        for (String ext : possibleExtensions) {
            String candidateFileName = baseName + "." + ext;
            Path filePath = new Path("/processed_data/" + candidateFileName);
            try {
                if (fs.exists(filePath)) {
                    logger.info("找到匹配的文件: {}", filePath.toString());
                    return candidateFileName;
                } else {
                    logger.debug("尝试匹配扩展名 {}，文件不存在: {}", ext, filePath.toString());
                }
            } catch (IOException e) {
                logger.error("检查文件 {} 失败: {}", filePath.toString(), e.getMessage(), e);
            }
        }

        // 如果以上扩展名都未匹配，尝试列出目录，查找文件名以 baseName 开头的文件
        try {
            FileStatus[] files = fs.listStatus(new Path("/processed_data/"));
            for (FileStatus file : files) {
                String fileName = file.getPath().getName();
                if (fileName.startsWith(baseName + ".")) {
                    logger.info("通过目录扫描找到匹配的文件: {}", file.getPath().toString());
                    return fileName;
                }
            }
        } catch (IOException e) {
            logger.error("列出目录 /processed_data/ 失败: {}", e.getMessage(), e);
        }

        logger.warn("未找到匹配的文件: {}", baseName);
        return null;  // 如果找不到匹配的文件，返回 null
    }

    // 预览文本文件内容
    private ResponseEntity<?> previewTextFile(FileSystem fs, Path filePath) throws IOException {
        FSDataInputStream inputStream = fs.open(filePath);
        String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        return ResponseEntity.ok(content);
    }

    // 预览图片文件（直接返回图片流）
    private ResponseEntity<?> previewImage(FileSystem fs, Path filePath) throws IOException {
        FSDataInputStream inputStream = fs.open(filePath);
        byte[] content = IOUtils.toByteArray(inputStream);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(content);  // 这里可以根据文件类型设置不同的 contentType
    }

    // 预览 PDF 文件
    private ResponseEntity<?> previewPDF(FileSystem fs, Path filePath) throws IOException {
        FSDataInputStream inputStream = fs.open(filePath);
        byte[] content = IOUtils.toByteArray(inputStream);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(content);
    }

    // 预览音频文件
    private ResponseEntity<?> previewAudio(FileSystem fs, Path filePath) throws IOException {
        FSDataInputStream inputStream = fs.open(filePath);
        byte[] content = IOUtils.toByteArray(inputStream);
        return ResponseEntity.ok().contentType(MediaType.valueOf("audio/mpeg")).body(content);
    }

    // 预览视频文件
    private ResponseEntity<?> previewVideo(FileSystem fs, Path filePath) throws IOException {
        FSDataInputStream inputStream = fs.open(filePath);
        byte[] content = IOUtils.toByteArray(inputStream);
        return ResponseEntity.ok().contentType(MediaType.valueOf("video/mp4")).body(content);
    }
}