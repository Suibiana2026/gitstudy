/**
 * HDFS 文件信息
 */

package com.bigData.main.pojo.HDFS;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class  FileInfo {
    private String name;  //文件名称
    private String type;        // 文件类型
    private long size;    //文件大小
    private Date modifyTime; //文件最后一次修改的时间戳
//    private String permission; //文件的访问权限
}