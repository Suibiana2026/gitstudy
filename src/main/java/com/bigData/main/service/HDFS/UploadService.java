package com.bigData.main.service.HDFS;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@Service
public class UploadService {
    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);

    @Value("${hadoop.fs.defaultFS}")
    private String hdfsDefaultFS;

    @Value("${hadoop.hdfs.original-data}")
    private String hdfsOriginalDataPath;

    private FileSystem fs;
    private Configuration conf;

    // 用于格式化时间成 ISO 8601 格式
    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    static {
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public UploadService() {
        // 构造函数中不进行任何需要依赖注入值的操作
    }

    // 服务销毁时关闭 FileSystem
    @PreDestroy
    public void close() throws IOException {
        if (fs != null) {
            fs.close();
        }
    }

    @PostConstruct
    public void init() throws IOException {
        if (hdfsDefaultFS == null || hdfsDefaultFS.isEmpty()) {
            throw new IllegalStateException("hadoop.fs.defaultFS must be configured");
        }
        if (hdfsOriginalDataPath == null || hdfsOriginalDataPath.isEmpty()) {
            throw new IllegalStateException("hadoop.hdfs.original-data must be configured");
        }

        this.conf = new Configuration();
        this.conf.set("fs.defaultFS", hdfsDefaultFS);
        // 本 Service 会在 @PreDestroy 里 close，因此必须持有【独立】实例，
        // 不能用 FileSystem.get()（那是全局共享单例，关掉会连累其它模块）。
        this.fs = FileSystem.newInstance(conf);
    }
    // 原有的多文件上传方法，修改为使用共享的 FileSystem
    public List<Map<String, Object>> uploadFilesToHdfs(MultipartFile[] files, String path) throws IOException {
        List<Map<String, Object>> uploadedFiles = new ArrayList<>();
        String hdfsPath = path != null ? path : hdfsOriginalDataPath;
        if (!hdfsPath.endsWith("/")) {
            hdfsPath += "/";
        }

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            String fullPath = hdfsDefaultFS + hdfsPath + fileName;
            logger.info("上传文件到 HDFS: {}", fullPath);
            uploadSingleFile(file, fullPath);
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", fileName);
            fileInfo.put("path", fullPath);
            fileInfo.put("type", fileName.substring(fileName.lastIndexOf(".") + 1));
            fileInfo.put("size", file.getSize());
            fileInfo.put("modifyTime", System.currentTimeMillis());
            uploadedFiles.add(fileInfo);
        }
        return uploadedFiles;
    }

    private void uploadSingleFile(MultipartFile file, String hdfsPath) throws IOException {
        Path path = new Path(hdfsPath);
        if (fs.exists(path)) {
            fs.delete(path, true);
        }
        try (FSDataOutputStream out = fs.create(path)) {
            out.write(file.getBytes());
        }
    }

    // 存储 WebSocket JSON 数据
    public Map<String, Object> uploadJsonToHdfs(String jsonData, String fileName) throws IOException {
        String fullPath = hdfsDefaultFS + hdfsOriginalDataPath + "/" + fileName;
        logger.info("上传 JSON 数据到 HDFS: {}", fullPath);

        Path path = new Path(fullPath);
        if (!fs.exists(new Path(hdfsOriginalDataPath))) {
            fs.mkdirs(new Path(hdfsOriginalDataPath));
        }
        if (fs.exists(path)) {
            fs.delete(path, true);
        }
        try (FSDataOutputStream out = fs.create(path)) {
            out.write(jsonData.getBytes());
        }

        FileStatus fileStatus = fs.getFileStatus(path);
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("name", fileName);
        fileInfo.put("path", fullPath);
        fileInfo.put("type", "json");
        fileInfo.put("size", fileStatus.getLen());
        fileInfo.put("modifyTime", ISO_FORMAT.format(new Date(fileStatus.getModificationTime())));
        return fileInfo;
    }

    // 列出 HDFS 文件
    public List<Map<String, Object>> listFiles() throws IOException {
        FileStatus[] fileStatuses = fs.listStatus(new Path(hdfsOriginalDataPath));
        List<Map<String, Object>> fileList = new ArrayList<>();

        for (FileStatus status : fileStatuses) {
            if (status.isFile()) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", status.getPath().getName());
                fileInfo.put("path", status.getPath().toString());
                fileInfo.put("type", status.getPath().getName().split("\\.")[1]);
                fileInfo.put("size", status.getLen());
                fileInfo.put("modifyTime", ISO_FORMAT.format(new Date(status.getModificationTime())));
                fileList.add(fileInfo);
            }
        }
        return fileList;
    }

}