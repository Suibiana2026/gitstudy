package com.bigData.main.pojo.HDFS;

import lombok.Data;

import java.util.List;

@Data
public class FolderInfo {
    private String path;
    private List<FileInfo> files;
}
