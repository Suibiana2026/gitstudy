/**
 * 用于删除 HDFS /processed_data 目录下的文件
 * DELETE 请求
 * 接口: /HDFS/delete/{fileName}
 */

package com.bigData.main.controller.HDFS.ProcessedData;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RestController
@RequestMapping("/HDFS")
public class DeleteFile2 {
    @Value("${hadoop.fs.defaultFS}")
    private String hdfsDefaultFS;

    @Value("${hadoop.user.name}")
    private String hdfsDefaultUser;
    // 定义日志记录器
    private static final Logger logger = LoggerFactory.getLogger(DeleteFile2.class);

    /**
     * 删除 HDFS 中的文件
     *
     * @param fileName 文件名
     * @return 删除操作的结果
     */

    @DeleteMapping("/deleteProcessedData/{fileName}")
    public ResponseEntity<?> deleteFile(@PathVariable String fileName) {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", hdfsDefaultFS);

        try (FileSystem fs = FileSystem.newInstance(new URI(hdfsDefaultFS), conf, hdfsDefaultUser)) {

            // 1. 记录收到的文件名
            logger.info("收到文件删除请求，原始文件名: {}", fileName);
            String decodedFileName = URLDecoder.decode(fileName, "UTF-8").trim();
            logger.info("解码后的文件名: {}", decodedFileName);

            // 2. 获取 HDFS 目录下的所有文件
            Path dirPath = new Path("/processed_data/");
            FileStatus[] fileStatuses = fs.listStatus(dirPath);

            // 3. 遍历文件列表，匹配文件（忽略后缀）
            Path matchedFile = null;
            for (FileStatus fileStatus : fileStatuses) {
                String hdfsFileName = fileStatus.getPath().getName();
                if (hdfsFileName.startsWith(decodedFileName + ".")) { // 文件名前缀匹配
                    matchedFile = fileStatus.getPath();
                    logger.info("找到匹配的 HDFS 文件: {}", matchedFile);
                    break;
                }
            }

            // 4. 如果找不到匹配的文件
            if (matchedFile == null) {
                logger.warn("文件不存在: {}", decodedFileName);
                return ResponseEntity.status(404).body("文件不存在");
            }

            // 5. 删除文件
            boolean isDeleted = fs.delete(matchedFile, false);
            if (isDeleted) {
                logger.info("文件删除成功: {}", matchedFile);
                return ResponseEntity.ok("文件删除成功");
            } else {
                return ResponseEntity.status(500).body("删除文件失败");
            }

        } catch (Exception e) {
            logger.error("删除文件时发生错误", e);
            return ResponseEntity.status(500).body("服务异常");
        }
    }
}