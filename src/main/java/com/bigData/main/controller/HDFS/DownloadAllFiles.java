/**
 * HDFS 一键打包文件为zip并下载
 * 方式: Get
 *
 */

package com.bigData.main.controller.HDFS;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.io.IOUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/HDFS/downloadAll")
public class DownloadAllFiles {
    private static final String HDFS_URI = "hdfs://zyh120:8020";

    @GetMapping
    public void downloadAllFiles(@RequestParam String folderPath, HttpServletResponse response) {
        try {
            // 1. 配置HDFS客户端
            Configuration conf = new Configuration();
            conf.set("fs.defaultFS", HDFS_URI);
            conf.setInt("dfs.client.socket-timeout", 300_000);

            // 2. 获取HDFS文件系统实例（newInstance 创建独立实例，避免 close 掉全局共享单例）
            try (FileSystem fs = FileSystem.newInstance(URI.create(HDFS_URI), conf);
                 OutputStream out = response.getOutputStream()) {

                // 3. 设置响应头
                response.setContentType("application/zip");
                response.setHeader("Content-Disposition",
                        "attachment; filename=\"" + new Path(folderPath).getName() + ".zip\"");

                // 4. 创建Zip输出流
                try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
                    // 5. 遍历文件夹并压缩文件
                    FileStatus[] fileStatuses = fs.listStatus(new Path(folderPath));
                    for (FileStatus status : fileStatuses) {
                        if (status.isFile()) {
                            // 添加文件到Zip
                            ZipEntry zipEntry = new ZipEntry(status.getPath().getName());
                            zipOut.putNextEntry(zipEntry);

                            // 写入文件内容
                            try (FSDataInputStream in = fs.open(status.getPath())) {
                                IOUtils.copyBytes(in, zipOut, 4096, false);
                            }
                            zipOut.closeEntry();
                        }
                    }
                }
            }

        } catch (FileNotFoundException e) {
            response.setStatus(404);
        } catch (Exception e) {
            response.setStatus(500);
        }
    }
}