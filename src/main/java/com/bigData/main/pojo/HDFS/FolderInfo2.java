package com.bigData.main.pojo.HDFS;

import lombok.Data;

import java.util.List;

@Data
public class FolderInfo2 {
    private String path;
    private List<FileInfo2> files;
}
