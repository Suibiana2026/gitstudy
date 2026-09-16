package com.bigData.main.controller.HDFS;

import com.bigData.main.service.HDFS.UploadService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/HDFS")
@CrossOrigin(origins = "*")
public class UploadFileController {

    private static final Logger logger = LoggerFactory.getLogger(UploadFileController.class);

    @Autowired
    private UploadService hdfsService;

    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static {
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    // 文件上传
    @PostMapping("/uploadToHDFS")
    public ResponseEntity<Object> uploadFile(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "path", required = false) String path) {
        try {
            logger.info("收到文件上传请求，路径: {}, 文件数量: {}", path, files.length);
            if (files == null || files.length == 0) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "No files provided");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            List<Map<String, Object>> uploadedFiles = hdfsService.uploadFilesToHdfs(files, path);
            if (uploadedFiles.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "No valid files uploaded");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("files", uploadedFiles);
            logger.info("文件上传成功，上传文件数: {}", uploadedFiles.size());
            return ResponseEntity.ok(successResponse);
        } catch (IllegalArgumentException e) {
            logger.error("上传参数错误: {}", e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid parameter: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("文件上传失败: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/saveProcessedFile")
    public ResponseEntity<Object> saveProcessedFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("operationType") String operationType,
            @RequestParam("field") String field) {
        try {
            // 验证输入参数
            if (file.isEmpty()) {
                throw new IllegalArgumentException("文件内容为空");
            }
            if (operationType == null || operationType.trim().isEmpty()) {
                throw new IllegalArgumentException("操作类型不能为空");
            }
            if (field == null || field.trim().isEmpty()) {
                throw new IllegalArgumentException("字段不能为空");
            }

            // 设置HDFS配置
            Configuration conf = new Configuration();
            FileSystem fs = FileSystem.get(URI.create("hdfs://zyh120:8020"), conf);

            // 创建processed_data目录（如果不存在）
            Path processDir = new Path("/processed_data");
            if (!fs.exists(processDir)) {
                fs.mkdirs(processDir);
                logger.info("创建HDFS目录: {}", processDir);
            }

            // 生成有意义的文件名
            String fileName = String.format("%s_%s_%d.csv",
                    operationType,
                    field.replaceAll("[^a-zA-Z0-9]", "_"),
                    System.currentTimeMillis());
            Path filePath = new Path(processDir, fileName);

            // 写入文件到HDFS
            try (OutputStream os = fs.create(filePath)) {
                os.write(file.getBytes());
                logger.info("文件保存成功: {}", filePath);
            }

            // 返回成功响应
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("filePath", filePath.toString());
            response.put("fileName", fileName);
            response.put("operationType", operationType);
            response.put("field", field);
            response.put("size", file.getSize());
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存处理文件失败: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Save processed file failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}