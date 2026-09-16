/**
 * 返回 HDFS /processed_data 目录下的所有文件(文件名称、文件类型、文件大小、上传日期)
 * Get 请求
 * 接口: /HDFS/ListProcessedDataFiles
 */

package com.bigData.main.controller.HDFS.ProcessedData;

import com.bigData.main.pojo.HDFS.FileInfo;
import com.bigData.main.pojo.HDFS.FolderInfo;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/HDFS/ListProcessedDataFiles")
public class FileLists2 {
    @Value("${hadoop.fs.defaultFS}")
    private String hdfsDefaultFS;

    @Value("${hadoop.user.name}")
    private String hdfsDefaultUser;

    @GetMapping
    public ResponseEntity<?> listFiles(@RequestParam(defaultValue = "/processed_data") String dirs) {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", hdfsDefaultFS);
        List<FolderInfo> result = new ArrayList<>();

        try (FileSystem fs = FileSystem.newInstance(new URI(hdfsDefaultFS), conf, hdfsDefaultUser)) {
            // 遍历每个目录路径
            for (String dir : dirs.split(",")) {
                Path path = validatePath(dir.trim());
                FolderInfo folder = new FolderInfo();
                folder.setPath(dir); // 设置文件夹路径

                // 检查文件夹是否存在，并获取文件信息
                if (fs.exists(path)) {
                    FileStatus[] files = fs.listStatus(path);
                    folder.setFiles(processFileStatus(files)); // 设置文件列表
                }
                result.add(folder);  // 将文件夹添加到结果中
            }

            return ResponseEntity.ok(result);  // 返回文件夹列表
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(404).body("路径不存在");
        } catch (IllegalArgumentException | URISyntaxException e) {
            return ResponseEntity.badRequest().body("非法路径格式");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("服务异常");
        }
    }

    private List<FileInfo> processFileStatus(FileStatus[] files) {
        return Arrays.stream(files)
                .filter(f -> !f.isDirectory())  // 只保留文件，不包含文件夹
                .map(status -> new FileInfo(
                        removeFileExtension(status.getPath().getName()),   // 获取文件名（去除后缀）
                        getFileType(status.getPath().getName()),  // 获取文件类型
                        status.getLen(),  // 文件大小
                        new Date(status.getModificationTime()) // 上传时间
                ))
                .collect(Collectors.toList());
    }

    private String getFileType(String fileName) {
        String fileType = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            fileType = fileName.substring(dotIndex + 1).toUpperCase();  // 获取文件类型（后缀）
        }
        return fileType;
    }

    private String removeFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            return fileName.substring(0, dotIndex);  // 去除文件后缀名
        }
        return fileName;  // 如果没有后缀名，返回原始文件名
    }

    private Path validatePath(String rawPath) {
        if (!rawPath.matches("^/processed_data(/.*)?$")) {
            throw new IllegalArgumentException("路径越界");
        }
        Path path = new Path(rawPath);
        if (!path.isAbsoluteAndSchemeAuthorityNull()) {
            throw new IllegalArgumentException("路径必须为绝对路径");
        }
        return path;
    }
}
