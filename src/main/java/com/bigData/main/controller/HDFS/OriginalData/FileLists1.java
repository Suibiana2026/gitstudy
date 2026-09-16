package com.bigData.main.controller.HDFS.OriginalData;

import com.bigData.main.pojo.HDFS.FileInfo;
import com.bigData.main.pojo.HDFS.FolderInfo;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/HDFS/ListOriginalDataFiles")
public class FileLists1 {

    private final FileSystem fileSystem;

    @Autowired
    public FileLists1(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @GetMapping
    public ResponseEntity<?> listFiles(@RequestParam(defaultValue = "/original_data") String dirs) {
        List<FolderInfo> result = new ArrayList<>();

        try {
            // 遍历每个目录路径
            for (String dir : dirs.split(",")) {
                Path path = validatePath(dir.trim());
                FolderInfo folder = new FolderInfo();
                folder.setPath(dir); // 设置文件夹路径

                // 检查文件夹是否存在，并获取文件信息
                if (fileSystem.exists(path)) {
                    FileStatus[] files = fileSystem.listStatus(path);
                    folder.setFiles(processFileStatus(files)); // 设置文件列表
                } else {
                    throw new FileNotFoundException("Path not found: " + dir);
                }
                result.add(folder); // 将文件夹添加到结果中
            }

            return ResponseEntity.ok(result); // 返回文件夹列表
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Path not found: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid path format: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Server error: " + e.getMessage());
        }
    }

    private List<FileInfo> processFileStatus(FileStatus[] files) {
        return Arrays.stream(files)
                .filter(fileStatus -> !fileStatus.isDirectory()) // 只保留文件，不包含文件夹
                .map(fileStatus -> new FileInfo(
                        removeFileExtension(fileStatus.getPath().getName()), // 获取文件名（去除后缀）
                        getFileType(fileStatus.getPath().getName()), // 获取文件类型
                        fileStatus.getLen(), // 文件大小
                        new Date(fileStatus.getModificationTime()) // 上传时间
                ))
                .collect(Collectors.toList());
    }

    private String getFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toUpperCase(); // 获取文件类型（后缀）
        }
        return ""; // 如果没有后缀，返回空字符串
    }

    private String removeFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            return fileName.substring(0, dotIndex); // 去除文件后缀名
        }
        return fileName; // 如果没有后缀名，返回原始文件名
    }

    private Path validatePath(String rawPath) {
        // 确保路径以 /original_data 开头，避免越界访问
        if (!rawPath.matches("^/original_data(/.*)?$")) {
            throw new IllegalArgumentException("Path must start with /original_data");
        }
        Path path = new Path(rawPath);
        // 确保路径是绝对路径且不包含非法的 URI 方案
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Path must be absolute");
        }
        return path;
    }
}