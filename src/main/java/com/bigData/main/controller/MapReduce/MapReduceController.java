package com.bigData.main.controller.MapReduce;

import com.bigData.main.service.MapReduce.MapReduceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/MapReduce")
@CrossOrigin(origins = "*")
public class MapReduceController {

    private static final Logger logger = LoggerFactory.getLogger(MapReduceController.class);

    @Autowired
    private MapReduceService mapReduceService;

    @PostMapping("/WordFrequencyStatistics")
    public ResponseEntity<Object> wordFrequencyStatistics(
            @RequestBody Map<String, Object> requestBody) {
        try {
            String filePaths = (String) requestBody.get("filePaths");
            String field = (String) requestBody.get("field");

            if (filePaths == null || filePaths.isEmpty()) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            if (field == null || field.isEmpty()) {
                throw new IllegalArgumentException("字段不能为空");
            }

            Map<String, Map<String, Integer>> result = mapReduceService.wordFrequencyStatistics(filePaths, field);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("词频统计失败: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Word frequency statistics failed: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/DuplicationEliminating")
    public ResponseEntity<Object> duplicationEliminating(
            @RequestBody Map<String, Object> requestBody) {
        try {
            String filePaths = (String) requestBody.get("filePaths");
            String dedupFields = (String) requestBody.get("dedupFields");

            if (filePaths == null || filePaths.isEmpty()) {
                throw new IllegalArgumentException("文件路径不能为空");
            }
            if (dedupFields == null || dedupFields.isEmpty()) {
                throw new IllegalArgumentException("去重字段不能为空");
            }

            String resultPath = mapReduceService.duplicationEliminating(filePaths, dedupFields);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "去重处理完成");
            response.put("resultPath", resultPath);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("去重处理失败: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Duplication eliminating failed: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/saveProcessedFile")
    public ResponseEntity<Object> saveProcessedFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("operationType") String operationType,
            @RequestParam("field") String field) {
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("文件内容为空");
            }
            if (operationType == null || operationType.trim().isEmpty()) {
                throw new IllegalArgumentException("操作类型不能为空");
            }
            if (field == null || field.trim().isEmpty()) {
                throw new IllegalArgumentException("字段不能为空");
            }

            String resultPath = mapReduceService.saveProcessedFile(file, operationType, field);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("filePath", resultPath);
            response.put("fileName", file.getOriginalFilename());
            response.put("operationType", operationType);
            response.put("field", field);
            response.put("size", file.getSize());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存处理文件失败: {}", e.getMessage(), e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Save processed file failed: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}